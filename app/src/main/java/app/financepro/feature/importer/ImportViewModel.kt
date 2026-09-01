package app.financepro.feature.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.ingest.Avaliada
import app.financepro.data.ingest.Candidata
import app.financepro.data.ingest.CsvFormato
import app.financepro.data.ingest.FormatoDeArquivo
import app.financepro.data.ingest.ImportRepository
import app.financepro.data.ingest.LinhaParaGravar
import app.financepro.data.ingest.MapeamentoCsv
import app.financepro.data.ingest.OfxStatement
import app.financepro.data.ingest.Veredito
import app.financepro.data.ingest.assinaturaCsv
import app.financepro.data.ingest.avaliar
import app.financepro.data.ingest.candidatasDoCsv
import app.financepro.data.ingest.candidatasDoOfx
import app.financepro.data.ingest.farejarCsv
import app.financepro.data.ingest.formatoDe
import app.financepro.data.ingest.lerCsv
import app.financepro.data.ingest.palpiteDeMapeamento
import app.financepro.data.ingest.parseOfx
import app.financepro.data.ingest.textoDeCsv
import app.financepro.data.prefs.ImportPrefs
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Os passos do fluxo de ingestao.md §1.1, na ordem. */
enum class PassoDaImportacao { CONTA, ARQUIVO, MAPEAMENTO, EXTRATO, REVISAO, PRONTO }

/**
 * Uma linha esperando o "confirmar". REQ-IMP-010
 *
 * [incluir] começa **ligada**, inclusive para possível duplicata: a spec proíbe
 * descartar por heurística justamente porque isso perde transação legítima
 * (REQ-IMP-009), e um padrão desligado seria o app decidindo por omissão o que
 * ele não pode decidir por ação.
 */
data class LinhaEmRevisao(
    val avaliada: Avaliada,
    val categoriaId: Long?,
    val incluir: Boolean = true,
)

/**
 * O que a importação precisa saber. REQ-IMP-001 · REQ-IMP-005 · REQ-IMP-010
 *
 * Um estado só, com o passo dentro dele (arquitetura.md §3, regra 3): são cinco
 * telas em sequência, e um `StateFlow` por passo deixaria a tela de revisão
 * montada com o arquivo do passo anterior por um quadro.
 */
data class ImportState(
    val passo: PassoDaImportacao = PassoDaImportacao.CONTA,
    val contas: List<Account> = emptyList(),
    val categorias: List<Category> = emptyList(),
    val contaId: Long? = null,
    val nomeDoArquivo: String = "",
    val tipo: String = "",
    /** Só CSV: a tabela crua e o que o farejador entendeu dela. */
    val tabela: List<List<String>> = emptyList(),
    val formato: CsvFormato? = null,
    val mapa: MapeamentoCsv? = null,
    /** Só OFX com mais de uma conta no arquivo. */
    val extratos: List<OfxStatement> = emptyList(),
    val linhas: List<LinhaEmRevisao> = emptyList(),
    /** Quantas o dedupe descartou sozinho — chave exata (REQ-IMP-007/008). */
    val descartadas: Int = 0,
    val gravadas: Int = 0,
    val recado: String? = null,
    val trabalhando: Boolean = false,
) {
    val conta: Account? get() = contas.firstOrNull { it.id == contaId }

    val incluidas: List<LinhaEmRevisao> get() = linhas.filter { it.incluir }

    /** REQ-TXN-005 — receita e despesa exigem categoria, e o lote não foge disso. */
    val semCategoria: Int get() = incluidas.count { it.categoriaId == null }

    val possiveis: Int
        get() = linhas.count { it.avaliada.veredito == Veredito.POSSIVEL_DUPLICATA }

    val podeConfirmar: Boolean
        get() = !trabalhando && incluidas.isNotEmpty() && semCategoria == 0

    /** As três primeiras linhas, que a tela de mapeamento exibe. REQ-IMP-005 */
    val previa: List<List<String>> get() = tabela.take(PREVIA)

    val colunas: Int get() = tabela.maxOfOrNull { it.size } ?: 0

    fun categoriaDe(id: Long?): Category? = categorias.firstOrNull { it.id == id }

    private companion object {
        const val PREVIA = 3
    }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    contas: AccountRepository,
    categorias: CategoryRepository,
    private val importacao: ImportRepository,
    private val prefs: ImportPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(contas.observeActive(), categorias.observeActive()) { cs, cats -> cs to cats }
                .collect { (cs, cats) ->
                    _state.update { it.copy(contas = cs, categorias = cats) }
                }
        }
    }

    fun escolherConta(id: Long) =
        _state.update { it.copy(contaId = id, passo = PassoDaImportacao.ARQUIVO, recado = null) }

    /**
     * Lê o arquivo e decide o próximo passo. REQ-IMP-001 · REQ-IMP-002
     *
     * O formato sai da **assinatura** do conteúdo, não da extensão: OFX
     * exportado como `.txt` é comum, e o seletor devolve
     * `application/octet-stream` para meio mundo.
     */
    fun arquivoEscolhido(origem: Uri) {
        val contaId = _state.value.contaId ?: return
        trabalhando {
            val bytes = importacao.ler(origem)
            val nome = importacao.nomeDe(origem)
            when (formatoDe(bytes)) {
                FormatoDeArquivo.OFX -> abrirOfx(parseOfx(bytes), nome, contaId)
                FormatoDeArquivo.CSV -> abrirCsv(textoDeCsv(bytes), nome)
            }
        }
    }

    /** REQ-IMP-002 — arquivo com mais de uma conta: quem escolhe é o usuário. */
    fun escolherExtrato(indice: Int) {
        val atual = _state.value
        val extrato = atual.extratos.getOrNull(indice) ?: return
        val contaId = atual.contaId ?: return
        trabalhando { revisar(candidatasDoOfx(extrato, contaId), contaId) }
    }

    fun mapear(mapa: MapeamentoCsv) = _state.update { it.copy(mapa = mapa, recado = null) }

    /** REQ-IMP-005 — o mapeamento confirmado fica guardado para a próxima vez. */
    fun confirmarMapeamento() {
        val atual = _state.value
        val mapa = atual.mapa
        val formato = atual.formato
        val contaId = atual.contaId
        if (mapa == null || formato == null || contaId == null) return
        trabalhando {
            prefs.lembrar(assinaturaCsv(atual.tabela), mapa)
            revisar(candidatasDoCsv(atual.tabela, formato, mapa, contaId), contaId)
        }
    }

    fun alternar(indice: Int) = _state.update { atual ->
        atual.copy(
            linhas = atual.linhas.mapIndexed { i, linha ->
                if (i == indice) linha.copy(incluir = !linha.incluir) else linha
            },
        )
    }

    fun categoria(indice: Int, categoriaId: Long) = _state.update { atual ->
        atual.copy(
            linhas = atual.linhas.mapIndexed { i, linha ->
                if (i == indice) linha.copy(categoriaId = categoriaId) else linha
            },
        )
    }

    /**
     * Grava o que o usuário confirmou. REQ-IMP-010 · REQ-IMP-011 · Art. 14
     *
     * É o **único** ponto do fluxo que escreve transação, e ele só existe atrás
     * de um botão que a tela de revisão desabilita enquanto houver linha sem
     * categoria.
     */
    fun confirmar() {
        val atual = _state.value
        val contaId = atual.contaId ?: return
        if (!atual.podeConfirmar) return
        trabalhando {
            val quantas = importacao.gravar(
                accountId = contaId,
                origem = atual.nomeDoArquivo,
                tipo = atual.tipo,
                linhas = atual.incluidas.map { LinhaParaGravar(it.avaliada.candidata, it.categoriaId) },
            )
            _state.update {
                it.copy(passo = PassoDaImportacao.PRONTO, gravadas = quantas, trabalhando = false)
            }
        }
    }

    /** Volta ao começo mantendo contas e categorias, que não vieram do arquivo. */
    fun recomecar() = _state.update {
        ImportState(contas = it.contas, categorias = it.categorias)
    }

    private suspend fun abrirOfx(extratos: List<OfxStatement>, nome: String, contaId: Long) {
        _state.update { it.copy(nomeDoArquivo = nome, tipo = "OFX", extratos = extratos) }
        when (extratos.size) {
            0 -> _state.update {
                it.copy(trabalhando = false, recado = "Não achei transações neste arquivo.")
            }
            1 -> revisar(candidatasDoOfx(extratos.single(), contaId), contaId)
            else -> _state.update {
                it.copy(passo = PassoDaImportacao.EXTRATO, trabalhando = false)
            }
        }
    }

    /**
     * CSV sempre passa pelo mapeamento, mesmo com o palpite pronto: REQ-IMP-005
     * exige a pré-visualização e a correção manual, e o farejador erra em algum
     * banco. Um mapeamento guardado de uma importação anterior chega aqui já
     * escolhido, e aí o passo é um toque em "Continuar".
     */
    private suspend fun abrirCsv(texto: String, nome: String) {
        val formato = farejarCsv(texto)
        val tabela = lerCsv(texto, formato.separador)
        val lembrado = prefs.mapeamentoDe(assinaturaCsv(tabela))
        _state.update {
            it.copy(
                passo = PassoDaImportacao.MAPEAMENTO,
                nomeDoArquivo = nome,
                tipo = "CSV",
                tabela = tabela,
                formato = formato,
                mapa = lembrado ?: palpiteDeMapeamento(tabela, formato),
                trabalhando = false,
            )
        }
    }

    /**
     * Dedupe e sugestão de categoria, e daí para a revisão.
     * REQ-IMP-007 a REQ-IMP-009 · REQ-ACT-002
     *
     * As duplicatas exatas saem da lista e viram **um número**: a spec manda
     * descartá-las automaticamente, e trezentas linhas riscadas que ninguém pode
     * mexer só atrapalhariam quem precisa conferir as outras vinte.
     */
    private suspend fun revisar(candidatas: List<Candidata>, contaId: Long) {
        val avaliadas = avaliar(candidatas, importacao.jaGravadas(contaId))
        val novas = avaliadas.filter { it.veredito != Veredito.DUPLICATA }
        val linhas = novas.map {
            LinhaEmRevisao(it, importacao.sugerir(it.candidata.description))
        }
        _state.update {
            it.copy(
                passo = PassoDaImportacao.REVISAO,
                linhas = linhas,
                descartadas = avaliadas.size - novas.size,
                trabalhando = false,
                recado = if (linhas.isEmpty()) "Nada novo neste arquivo." else null,
            )
        }
    }

    /**
     * A falha vira frase na tela, não exceção. Arquivo trocado por engano,
     * `Uri` que expirou, cartão removido no meio da leitura — nenhum deles é bug
     * nosso, e todos acontecem com quem está tentando importar o extrato.
     */
    private fun trabalhando(bloco: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(trabalhando = true, recado = null) }
        runCatching { bloco() }.onFailure { erro ->
            _state.update {
                it.copy(
                    trabalhando = false,
                    recado = "Não deu para ler: ${erro.message ?: "erro desconhecido"}",
                )
            }
        }
    }
}
