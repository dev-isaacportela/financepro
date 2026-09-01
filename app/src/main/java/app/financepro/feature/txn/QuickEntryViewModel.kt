package app.financepro.feature.txn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.EscopoDeParcela
import app.financepro.domain.usecase.ValidationError
import app.financepro.domain.usecase.aplicarNasParcelas
import app.financepro.domain.usecase.parcelasNoEscopo
import app.financepro.domain.usecase.sanitize
import app.financepro.domain.usecase.splitInstallments
import app.financepro.domain.usecase.validateTxn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

/**
 * O que a folha de lançamento rápido precisa saber. REQ-UI-002 · REQ-UI-003
 *
 * Um único estado, não seis `StateFlow` (arquitetura.md §3, regra 3): campos
 * condicionais que aparecem e somem em emissões separadas piscariam a tela.
 *
 * As listas condicionais são **derivadas**, não guardadas em paralelo. Guardar
 * `categorias` ao lado de `tipo` obrigaria a atualizar as duas juntas em todo
 * lugar, e a primeira vez que alguém esquecesse o grid mostraria "Salário" numa
 * despesa.
 */
data class QuickEntryState(
    val cents: Long = 0,
    val tipo: TxnType = TxnType.EXPENSE,
    val contas: List<Account> = emptyList(),
    val contaId: Long? = null,
    val destinoId: Long? = null,
    val porTipo: Map<CategoryKind, List<Category>> = emptyMap(),
    val categoriaId: Long? = null,
    val descricao: String = "",
    val parcelas: Int = 1,
    /** A linha carregada do banco, quando isto é uma edição. Nula ao criar. */
    val original: Txn? = null,
    /** As irmãs de uma compra parcelada, quando [original] é uma delas. */
    val grupo: List<Txn> = emptyList(),
    /** REQ-TXN-009 — a que parcelas a edição se aplica. */
    val escopo: EscopoDeParcela = EscopoDeParcela.SO_ESTA,
    val erros: List<ValidationError> = emptyList(),
    val salvo: Boolean = false,
) {
    val conta: Account? get() = contas.firstOrNull { it.id == contaId }

    /** O grid do tipo corrente, ordenado por uso (REQ-CAT-006). */
    val categorias: List<Category>
        get() = porTipo[if (tipo == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE].orEmpty()

    val editando: Boolean get() = original != null

    /**
     * Editando uma parcela de uma compra parcelada. REQ-TXN-009
     *
     * A T-050 abriu estas somente leitura porque não havia quem perguntasse o
     * escopo; a T-027 é essa pergunta, e a folha passa a editar.
     */
    val ehParcela: Boolean get() = original?.installmentGroupId != null

    /**
     * REQ-UI-003 — os três campos condicionais, decididos num lugar só.
     *
     * Parcelar é da criação: `mostraParcelas` falso ao editar é o que mantém
     * `salvar` fora do ramo de [TxnRepository.salvarParcelado] sem uma segunda
     * guarda lá dentro.
     */
    val mostraParcelas: Boolean get() = conta?.isCard == true && tipo == TxnType.EXPENSE && !editando
    val mostraDestino: Boolean get() = tipo == TxnType.TRANSFER
    val mostraCategoria: Boolean get() = tipo != TxnType.TRANSFER

    fun erroDe(campo: ValidationError.Campo): String? =
        erros.firstOrNull { it.campo == campo }?.mensagem
}

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val contas: AccountRepository,
    private val categorias: CategoryRepository,
    private val txns: TxnRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickEntryState())
    val state: StateFlow<QuickEntryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                contas.observeActive(),
                categorias.observeByUse(CategoryKind.EXPENSE),
                categorias.observeByUse(CategoryKind.INCOME),
            ) { cs, despesa, receita ->
                Triple(cs, despesa, receita)
            }.collect { (cs, despesa, receita) ->
                _state.update {
                    it.copy(
                        contas = cs,
                        // Só na primeira emissão: sobrescrever depois trocaria a
                        // conta debaixo de quem já escolheu outra.
                        contaId = it.contaId ?: cs.firstOrNull()?.id,
                        porTipo = mapOf(
                            CategoryKind.EXPENSE to despesa,
                            CategoryKind.INCOME to receita,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Consome o sinal de salvo e devolve o formulário limpo.
     *
     * Sem isto a folha **nunca mais abre**: ela vive fora do `NavHost`, então o
     * `hiltViewModel()` dela é o da Activity e sobrevive ao fechamento com
     * `salvo = true`. Na reabertura o efeito que fecha ao salvar dispara na
     * primeira composição e a folha se fecha sozinha.
     *
     * Encontrado no aparelho, tocando o botão duas vezes — o primeiro
     * lançamento funciona, e é o segundo que não acontece.
     */
    fun concluido() = _state.update {
        QuickEntryState(contas = it.contas, contaId = it.contaId, porTipo = it.porTipo)
    }

    /**
     * Carrega a transação na folha. REQ-TXN-001
     *
     * [QuickEntryState.cents] recebe o valor **absoluto**: o sinal é convenção do
     * banco (REQ-TXN-002), e reabrir uma despesa com "−18,50" no campo pediria ao
     * usuário para entender uma convenção que a folha nunca mostrou.
     */
    fun editar(id: Long) = viewModelScope.launch {
        val txn = txns.byId(id) ?: return@launch
        _state.update {
            it.copy(
                original = txn,
                cents = abs(txn.amountCents),
                tipo = txn.type,
                contaId = txn.accountId,
                destinoId = txn.counterAccountId,
                categoriaId = txn.categoryId,
                descricao = txn.description,
                parcelas = 1,
                grupo = txn.installmentGroupId?.let { txns.grupoDeParcelas(it) }.orEmpty(),
                escopo = EscopoDeParcela.SO_ESTA,
                erros = emptyList(),
            )
        }
    }

    /** REQ-TXN-009 — o padrão é o menor estrago: só esta. */
    fun escopo(novo: EscopoDeParcela) = _state.update { it.copy(escopo = novo) }

    fun valor(cents: Long) = _state.update { it.copy(cents = cents, erros = emptyList()) }

    fun descricao(texto: String) = _state.update { it.copy(descricao = texto) }

    fun parcelas(n: Int) = _state.update { it.copy(parcelas = n) }

    fun conta(id: Long) = _state.update { it.copy(contaId = id, erros = emptyList()) }

    fun destino(id: Long) = _state.update { it.copy(destinoId = id, erros = emptyList()) }

    fun categoria(id: Long) = _state.update { it.copy(categoriaId = id, erros = emptyList()) }

    /**
     * Trocar o tipo derruba a categoria escolhida: manter "Alimentação" ao virar
     * receita produziria o erro de REQ-CAT-003 num campo que o usuário nem tocou.
     */
    fun tipo(novo: TxnType) =
        _state.update { it.copy(tipo = novo, categoriaId = null, erros = emptyList()) }

    /**
     * Valida e grava. REQ-CORE-002 · REQ-TXN-002 · REQ-CAT-006
     *
     * O sinal é aplicado **aqui**, na borda: o usuário digita `18,50` para uma
     * despesa e a coluna guarda `-1850`, que é a convenção de REQ-TXN-002. Pedir
     * o sinal a quem está com o cartão na mão seria mudar a convenção de lugar.
     *
     * [hoje] é parâmetro para o teste não depender do dia em que roda — mesma
     * razão de `validateTxn` recebê-lo em vez de ler o relógio.
     */
    fun salvar(hoje: LocalDate = LocalDate.now()) {
        val atual = _state.value
        val conta = atual.contaId ?: return
        val txn = sanitize(
            Txn(
                // `id` diferente de zero é o que faz o repositório atualizar em vez
                // de inserir — um `insert` no lugar do `update` duplicaria dinheiro
                // na tela sem erro nenhum.
                id = atual.original?.id ?: 0,
                accountId = conta,
                type = atual.tipo,
                amountCents = if (atual.tipo == TxnType.INCOME) atual.cents else -atual.cents,
                // Editar não move o lançamento para hoje: corrigir a descrição de
                // uma despesa de terça não pode fazê-la aparecer na quinta, nem
                // trocar o mês em que ela conta.
                date = atual.original?.date ?: hoje,
                counterAccountId = atual.destinoId,
                categoryId = atual.categoriaId,
                description = atual.descricao,
                // O que a folha não edita, o original decide. `cleared` é o caso
                // com consequência visível: um previsto virando pago sozinho
                // (REQ-TXN-006) mudaria o saldo sem ninguém pedir.
                cleared = atual.original?.cleared ?: true,
                installmentGroupId = atual.original?.installmentGroupId,
                installmentIndex = atual.original?.installmentIndex,
                installmentTotal = atual.original?.installmentTotal,
            ),
        )
        val erros = validateTxn(
            txn = txn,
            contas = atual.contas.associateBy { it.id },
            categorias = atual.porTipo.values.flatten().associateBy { it.id },
            hoje = hoje,
        )
        if (erros.isNotEmpty()) {
            _state.update { it.copy(erros = erros) }
            return
        }
        viewModelScope.launch {
            if (atual.ehParcela) {
                // REQ-TXN-009 — só o escopo escolhido muda. A data e o `id` de
                // cada irmã ficam de fora da propagação: espalhá-los colapsaria
                // as doze parcelas no mesmo dia.
                val alvos = parcelasNoEscopo(txn, atual.grupo, atual.escopo)
                txns.salvarVarias(aplicarNasParcelas(txn, alvos))
            } else if (atual.mostraParcelas && atual.parcelas > 1) {
                // A divisão é a da T-026, testada em 792 combinações — a sobra
                // cai na última parcela, nunca na primeira, porque é a primeira
                // que quem confere compara com o valor anunciado na compra.
                val partes = splitInstallments(txn.amountCents, atual.parcelas, hoje)
                txns.salvarParcelado(
                    partes.map {
                        txn.copy(
                            amountCents = it.amountCents,
                            date = it.date,
                            installmentIndex = it.index,
                            installmentTotal = it.count,
                        )
                    },
                )
            } else {
                txns.salvar(txn)
            }
            txn.categoryId?.let { categorias.registrarUso(it) }
            _state.update { it.copy(salvo = true) }
        }
    }
}
