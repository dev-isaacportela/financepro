package app.financepro.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.export.BackupIlegivel
import app.financepro.data.export.BackupRepository
import app.financepro.data.export.BaseExportada
import app.financepro.data.export.ExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** O que precisa ser digitado para apagar tudo. REQ-BAK-004 */
const val PALAVRA_DE_APAGAR = "APAGAR"

/**
 * Senha curta demais para proteger dez anos de histórico.
 *
 * Oito não é um número mágico, é o piso: com 600 mil iterações de PBKDF2 uma
 * senha de quatro caracteres ainda cai num dicionário offline em minutos, e o
 * arquivo vai justamente para onde o ataque é offline — nuvem, cartão, e-mail.
 */
const val SENHA_MINIMA = 8

/**
 * O que a tela de dados precisa saber. REQ-BAK-001 a REQ-BAK-004
 *
 * Um estado só, com os quatro campos de texto dentro dele (arquitetura.md §3,
 * regra 3). São três fluxos na mesma tela — exportar, backup, apagar — e cada um
 * com `StateFlow` próprio significaria três emissões fora de sincronia num
 * lugar onde uma das ações é irreversível.
 */
data class ExportState(
    val recado: String? = null,
    val trabalhando: Boolean = false,
    /** Senha do backup a criar, e a repetição que pega o erro de digitação. */
    val senha: String = "",
    val repetir: String = "",
    /** O arquivo escolhido para restaurar, e a senha dele. */
    val origem: Uri? = null,
    val senhaDeLeitura: String = "",
    /** Lida e decifrada, ainda **não** gravada. REQ-BAK-003 */
    val previa: BaseExportada? = null,
    val registrosAtuais: Int = 0,
    /** REQ-BAK-004 — o que foi digitado na confirmação. */
    val confirmacao: String = "",
) {
    val senhaCurta: Boolean get() = senha.isNotEmpty() && senha.length < SENHA_MINIMA
    val senhasDiferentes: Boolean get() = repetir.isNotEmpty() && senha != repetir

    val podeCriar: Boolean
        get() = !trabalhando && senha.length >= SENHA_MINIMA && senha == repetir

    val podeLer: Boolean get() = !trabalhando && origem != null && senhaDeLeitura.isNotEmpty()

    /** A digitação é exata, maiúsculas incluídas: é a última porta antes do fim. */
    val podeApagar: Boolean get() = !trabalhando && confirmacao == PALAVRA_DE_APAGAR
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportacao: ExportRepository,
    private val backup: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    fun exportarCsv(destino: Uri) = comRecado("transações") { exportacao.exportarCsv(destino) }

    fun exportarJson(destino: Uri) = comRecado("registros") { exportacao.exportarJson(destino) }

    fun senha(valor: String) = _state.update { it.copy(senha = valor, recado = null) }

    fun repetir(valor: String) = _state.update { it.copy(repetir = valor, recado = null) }

    fun senhaDeLeitura(valor: String) =
        _state.update { it.copy(senhaDeLeitura = valor, previa = null, recado = null) }

    fun confirmacao(valor: String) = _state.update { it.copy(confirmacao = valor) }

    /**
     * REQ-BAK-002 — cria o arquivo cifrado, e **esquece a senha** em seguida.
     *
     * Limpar os dois campos não é higiene teatral: a folha continua montada
     * depois de salvar, e uma senha deixada na tela é uma senha exibida para
     * quem pegar o aparelho destrancado no minuto seguinte.
     */
    fun criarBackup(destino: Uri) {
        val senha = _state.value.senha.toCharArray()
        comRecado("registros no backup") {
            backup.criar(destino, senha).also {
                _state.update { atual -> atual.copy(senha = "", repetir = "") }
            }
        }
    }

    fun escolherOrigem(origem: Uri) =
        _state.update { it.copy(origem = origem, previa = null, recado = null) }

    /**
     * Lê o arquivo e mostra o que ele traz, **sem** tocar no banco. REQ-BAK-003
     *
     * É o passo que separa "abri o arquivo" de "troquei minha base": a
     * confirmação precisa de números, e os números só existem depois de decifrar.
     */
    fun lerBackup() {
        val atual = _state.value
        val origem = atual.origem ?: return
        val senha = atual.senhaDeLeitura.toCharArray()
        trabalhando {
            val lida = backup.ler(origem, senha)
            val agora = backup.quantosRegistros()
            _state.update {
                it.copy(previa = lida, registrosAtuais = agora, trabalhando = false, recado = null)
            }
        }
    }

    /** REQ-BAK-003 — só aqui a base é sobrescrita, e só depois do confirmar. */
    fun confirmarRestauracao() {
        val lida = _state.value.previa ?: return
        trabalhando {
            backup.restaurar(lida)
            _state.update {
                ExportState(recado = "Base restaurada: ${lida.registros} registros.")
            }
        }
    }

    /** REQ-BAK-004 */
    fun apagarTudo() {
        if (!_state.value.podeApagar) return
        trabalhando {
            backup.apagarTudo()
            _state.update { ExportState(recado = "Tudo apagado. O app volta ao começo.") }
        }
    }

    private fun comRecado(unidade: String, bloco: suspend () -> Int) = trabalhando {
        val quantos = bloco()
        _state.update { it.copy(trabalhando = false, recado = "Pronto: $quantos $unidade.") }
    }

    /**
     * A falha vira **frase na tela**, não exceção que derruba o app.
     *
     * Escrita e leitura em `Uri` de outro app falham por motivos que não são bug
     * nosso — cartão removido, nuvem sem espaço, permissão revogada entre a
     * escolha e a gravação —, e senha errada é o caso **esperado** de
     * [BackupIlegivel]. Quem está tentando recuperar o próprio histórico precisa
     * de uma frase; um crash diria a mesma coisa do jeito mais confuso possível.
     */
    private fun trabalhando(bloco: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(trabalhando = true, recado = null) }
        runCatching { bloco() }.onFailure { erro ->
            val recado = when (erro) {
                is BackupIlegivel -> erro.message
                else -> "Não deu certo: ${erro.message ?: "erro desconhecido"}"
            }
            _state.update { it.copy(trabalhando = false, recado = recado) }
        }
    }
}
