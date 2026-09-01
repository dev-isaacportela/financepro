package app.financepro.feature.txn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.financepro.core.time.MonthRange
import app.financepro.core.time.monthRange
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.DiaDeTransacoes
import app.financepro.domain.usecase.EscopoDeParcela
import app.financepro.domain.usecase.Filtro
import app.financepro.domain.usecase.agruparPorDia
import app.financepro.domain.usecase.extrato
import app.financepro.domain.usecase.filtrar
import app.financepro.domain.usecase.parcelasNoEscopo
import app.financepro.feature.Transacoes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * O que a lista de transações precisa saber. REQ-TXN-010 · REQ-TXN-011 · REQ-TXN-012
 *
 * Um único estado (arquitetura.md §3, regra 3), e o que a tela consome é
 * **derivado** dele, não guardado em paralelo: manter `dias` ao lado de `filtro`
 * obrigaria a recalcular os dois juntos em toda ação, e o primeiro esquecimento
 * mostraria a lista de um filtro com o cabeçalho de outro. Mesma escolha do
 * [QuickEntryState].
 *
 * [todas] é o histórico inteiro, não a janela: o saldo corrente do extrato
 * precisa do que veio **antes** do mês exibido, e o mês é recortado depois.
 */
data class TransactionsState(
    val mes: YearMonth,
    /** "Tudo" ignora o mês. É o filtro que a ADR-009 nomeia ao fixar seu teto. */
    val periodoTodo: Boolean = false,
    val filtro: Filtro = Filtro(),
    val contas: List<Account> = emptyList(),
    val categorias: List<Category> = emptyList(),
    val todas: List<Txn> = emptyList(),
    /** Muda a cada exclusão. É o gatilho do snackbar de desfazer — ver [TransactionsViewModel.excluir]. */
    val exclusoes: Int = 0,
    /** Quantas linhas a última exclusão levou. Uma parcela pode levar doze. */
    val ultimaQuantidade: Int = 1,
    /** REQ-TXN-009 — a parcela esperando a escolha de escopo. */
    val excluindo: Txn? = null,
) {
    /**
     * ponytail: `monthStartDay` fixo em 1 até existir tela de ajustes.
     * `monthRange` já aceita o parâmetro (REQ-CORE-003); falta só de onde lê-lo.
     */
    val periodo: MonthRange get() = monthRange(mes)

    val doPeriodo: List<Txn> get() = if (periodoTodo) todas else todas.filter { it.date in periodo }

    val visiveis: List<Txn> get() = filtrar(doPeriodo, filtro)

    val dias: List<DiaDeTransacoes> get() = agruparPorDia(visiveis, filtro.contaId)

    /** A conta do filtro, quando há exatamente uma — é o que liga o extrato. */
    val conta: Account? get() = filtro.contaId?.let { id -> contas.firstOrNull { it.id == id } }

    /**
     * Saldo corrente por transação, só no extrato de uma conta. REQ-ACC-005
     *
     * Sai de [extrato] sobre o histórico **inteiro** da conta; recortar o mês
     * antes de acumular daria um saldo que não bate com nenhum extrato de banco.
     */
    val saldos: Map<Long, Long>
        get() = conta?.let { c ->
            extrato(c, todas.filter { it.accountId == c.id || it.counterAccountId == c.id })
                .associate { it.txn.id to it.saldoCents }
        }.orEmpty()

    fun contaDe(id: Long?): Account? = contas.firstOrNull { it.id == id }

    fun categoriaDe(id: Long?): Category? = categorias.firstOrNull { it.id == id }
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    contas: AccountRepository,
    categorias: CategoryRepository,
    private val txns: TxnRepository,
    estado: SavedStateHandle,
) : ViewModel() {

    /**
     * REQ-RPT-004 — a lista abre já filtrada quando vem de uma fatia do gráfico.
     *
     * O filtro entra no estado **inicial**, não num efeito depois da primeira
     * emissão: aplicá-lo em seguida mostraria a lista inteira por um quadro, e
     * é justamente o mês inteiro de despesas que o usuário não pediu para ver.
     * Vindo da aba, os dois argumentos são neutros e isto é `YearMonth.now()`
     * com filtro vazio, como antes.
     */
    private val rota = estado.toRoute<Transacoes>()

    private val _state = MutableStateFlow(
        TransactionsState(
            mes = rota.mesIso.takeIf { it.isNotBlank() }?.let(YearMonth::parse) ?: YearMonth.now(),
            filtro = Filtro(categoriaId = rota.categoriaId.takeIf { it != 0L }),
        ),
    )
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // `observeAll` e não `observeActive`: uma conta arquivada sai das
            // listas de seleção e do saldo (REQ-ACC-005), mas o histórico dela
            // continua de pé — e um extrato que esconde o passado da conta
            // arquivada é o oposto do que o requisito pede.
            combine(
                contas.observeAll(),
                categorias.observeActive(),
                txns.observeTudo(),
            ) { cs, cats, ts -> Triple(cs, cats, ts) }
                .collect { (cs, cats, ts) ->
                    _state.update { it.copy(contas = cs, categorias = cats, todas = ts) }
                }
        }
    }

    fun mesAnterior() = _state.update { it.copy(mes = it.mes.minusMonths(1), periodoTodo = false) }

    fun mesSeguinte() = _state.update { it.copy(mes = it.mes.plusMonths(1), periodoTodo = false) }

    fun irPara(mes: YearMonth) = _state.update { it.copy(mes = mes, periodoTodo = false) }

    fun todoOPeriodo(ligado: Boolean) = _state.update { it.copy(periodoTodo = ligado) }

    fun aplicar(filtro: Filtro) = _state.update { it.copy(filtro = filtro) }

    fun limparFiltros() = _state.update { it.copy(filtro = Filtro()) }

    /**
     * REQ-TXN-010 — exclui na hora, sem diálogo, e arma o desfazer.
     *
     * [TransactionsState.exclusoes] é contador e não bandeira de propósito: com
     * um booleano, excluir uma segunda linha antes dos 5s não reiniciaria o
     * efeito da tela — o snackbar da primeira continuaria contando, e a segunda
     * exclusão ficaria sem desfazer nenhum. Um valor que muda sempre reinicia.
     */
    fun excluir(txn: Txn) {
        // REQ-TXN-009 — parcela pergunta o escopo antes de qualquer escrita. A
        // linha só sai da tela quando o banco disser que ela saiu, nos dois
        // casos: quem manda na lista é o dado, não o gesto.
        if (txn.installmentGroupId != null) {
            _state.update { it.copy(excluindo = txn) }
        } else {
            viewModelScope.launch { apagar(listOf(txn.id)) }
        }
    }

    /** REQ-TXN-009 — só o escopo escolhido sai. */
    fun excluirComEscopo(escopo: EscopoDeParcela) = viewModelScope.launch {
        val alvo = _state.value.excluindo ?: return@launch
        val grupo = alvo.installmentGroupId?.let { txns.grupoDeParcelas(it) }.orEmpty()
        val ids = parcelasNoEscopo(alvo, grupo, escopo).map { it.id }
        _state.update { it.copy(excluindo = null) }
        apagar(ids)
    }

    fun cancelarExclusao() = _state.update { it.copy(excluindo = null) }

    private suspend fun apagar(ids: List<Long>) {
        txns.excluirVarias(ids)
        _state.update { it.copy(exclusoes = it.exclusoes + 1, ultimaQuantidade = ids.size) }
    }

    fun desfazer() = viewModelScope.launch { txns.desfazerExclusao() }

}
