package app.financepro.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.RecurringRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.Frequency
import app.financepro.domain.usecase.RecurrenceSpec
import app.financepro.domain.usecase.RecurringRule
import app.financepro.domain.usecase.ValidationError
import app.financepro.domain.usecase.occurrenceAt
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
 * A regra aberta na folha. REQ-REC-001 · REQ-REC-002 · REQ-REC-005
 *
 * Os campos são os do formulário, não os do domínio: [cents] é positivo porque
 * é o que se digita, e o sinal entra em [toRule], na borda — a mesma escolha de
 * `QuickEntryViewModel.salvar`, e pela mesma razão: pedir o sinal a quem está
 * cadastrando o aluguel seria mudar a convenção de lugar.
 */
data class RegraEmEdicao(
    val id: Long = 0,
    val cents: Long = 0,
    val tipo: TxnType = TxnType.EXPENSE,
    val contaId: Long? = null,
    val destinoId: Long? = null,
    val categoriaId: Long? = null,
    val descricao: String = "",
    val frequencia: Frequency = Frequency.MONTHLY,
    val intervalo: Int = 1,
    val inicio: LocalDate = LocalDate.now(),
    val fim: LocalDate? = null,
    val autoPost: Boolean = false,
    val ativa: Boolean = true,
) {
    val mostraDestino: Boolean get() = tipo == TxnType.TRANSFER
    val mostraCategoria: Boolean get() = tipo != TxnType.TRANSFER
    val editando: Boolean get() = id != 0L

    /**
     * Formulário para domínio, já saneado.
     *
     * O `when` faz aqui o que `sanitize` faz para transação (REQ-TXN-004):
     * destino só existe em transferência, categoria só existe fora dela. Sem
     * isto, uma regra que começou como despesa e virou transferência guardaria
     * a categoria antiga, e toda ocorrência gerada nasceria com ela.
     */
    fun toRule(): RecurringRule = RecurringRule(
        id = id,
        accountId = contaId ?: 0,
        type = tipo,
        amountCents = if (tipo == TxnType.INCOME) cents else -cents,
        description = descricao.trim(),
        spec = RecurrenceSpec(
            frequency = frequencia,
            startDate = inicio,
            interval = intervalo,
            endDate = fim,
        ),
        counterAccountId = if (mostraDestino) destinoId else null,
        categoryId = if (mostraCategoria) categoriaId else null,
        autoPost = autoPost,
        active = ativa,
    )
}

/**
 * O que a tela de recorrências precisa saber. REQ-REC-001 · REQ-REC-008
 *
 * Um único estado, e a lista de categorias **derivada** dele — mesma escolha de
 * `QuickEntryState`: guardá-la ao lado do tipo obrigaria a atualizar as duas
 * juntas, e o primeiro esquecimento ofereceria "Salário" numa despesa.
 */
data class RecurringState(
    val hoje: LocalDate = LocalDate.now(),
    val regras: List<RecurringRule> = emptyList(),
    val contas: List<Account> = emptyList(),
    val todasCategorias: List<Category> = emptyList(),
    val folha: RegraEmEdicao? = null,
    val erros: List<ValidationError> = emptyList(),
    /** Se a primeira emissão do banco já chegou — ver `TransactionsState.carregado`. */
    val carregado: Boolean = false,
) {
    /** O grid do tipo corrente (REQ-CAT-003): receita não usa categoria de despesa. */
    val categorias: List<Category>
        get() {
            val esperado =
                if (folha?.tipo == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            return todasCategorias.filter { it.kind == esperado }
        }

    fun contaDe(id: Long?): Account? = contas.firstOrNull { it.id == id }

    fun categoriaDe(id: Long?): Category? = todasCategorias.firstOrNull { it.id == id }

    fun erroDe(campo: ValidationError.Campo): String? =
        erros.firstOrNull { it.campo == campo }?.mensagem
}

@HiltViewModel
class RecurringViewModel @Inject constructor(
    contas: AccountRepository,
    categorias: CategoryRepository,
    private val regras: RecurringRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecurringState())
    val state: StateFlow<RecurringState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                contas.observeActive(),
                categorias.observeActive(),
                regras.observeAll(),
            ) { cs, cats, rs -> Triple(cs, cats, rs) }
                .collect { (cs, cats, rs) ->
                    _state.update {
                        it.copy(contas = cs, todasCategorias = cats, regras = rs, carregado = true)
                    }
                }
        }
    }

    /** [regra] nula abre a folha vazia, na primeira conta da lista. */
    fun abrir(regra: RecurringRule?) = _state.update { atual ->
        val folha = regra?.let {
            RegraEmEdicao(
                id = it.id,
                cents = abs(it.amountCents),
                tipo = it.type,
                contaId = it.accountId,
                destinoId = it.counterAccountId,
                categoriaId = it.categoryId,
                descricao = it.description,
                frequencia = it.spec.frequency,
                intervalo = it.spec.interval,
                inicio = it.spec.startDate,
                fim = it.spec.endDate,
                autoPost = it.autoPost,
                ativa = it.active,
            )
        } ?: RegraEmEdicao(contaId = atual.contas.firstOrNull()?.id, inicio = atual.hoje)
        atual.copy(folha = folha, erros = emptyList())
    }

    fun fechar() = _state.update { it.copy(folha = null, erros = emptyList()) }

    /**
     * Troca a folha inteira.
     *
     * Um setter por campo seriam treze funções numa classe só, treze linhas
     * idênticas exceto pelo nome do campo. A tela já tem a folha em mãos e faz
     * o `copy`; o que sobra aqui é a única coisa que não é cópia de campo —
     * limpar os erros do envio anterior assim que alguém mexe em algo.
     */
    fun editar(folha: RegraEmEdicao) = _state.update { it.copy(folha = folha, erros = emptyList()) }

    /**
     * Trocar o tipo derruba a categoria escolhida, mesma regra da folha de
     * lançamento: manter "Alimentação" ao virar receita produziria o erro de
     * REQ-CAT-003 num campo que o usuário nem tocou.
     */
    fun tipo(novo: TxnType) = _state.update {
        it.copy(folha = it.folha?.copy(tipo = novo, categoriaId = null), erros = emptyList())
    }

    /**
     * Valida e grava. REQ-REC-001 · REQ-REC-007
     *
     * A validação é a **da transação** (`validateTxn`), aplicada à ocorrência
     * que a regra produziria no dia de início. Uma regra que gera doze
     * lançamentos inválidos é uma regra inválida, e um segundo validador aqui
     * divergiria do primeiro na primeira mudança de mensagem.
     */
    fun salvar() {
        val atual = _state.value
        val folha = atual.folha ?: return
        val regra = folha.toRule()
        val erros = validateTxn(
            txn = regra.occurrenceAt(folha.inicio),
            contas = atual.contas.associateBy { it.id },
            categorias = atual.todasCategorias.associateBy { it.id },
            hoje = atual.hoje,
        )
        if (erros.isNotEmpty()) {
            _state.update { it.copy(erros = erros) }
            return
        }
        viewModelScope.launch {
            regras.salvar(regra, atual.hoje)
            _state.update { it.copy(folha = null, erros = emptyList()) }
        }
    }

    /** Some com a regra, e deixa as ocorrências já lançadas de pé. */
    fun excluir() {
        val regra = _state.value.folha?.takeIf { it.editando }?.toRule() ?: return
        viewModelScope.launch {
            regras.excluir(regra)
            _state.update { it.copy(folha = null, erros = emptyList()) }
        }
    }
}
