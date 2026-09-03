package app.financepro.feature.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.core.taxa.anualEfetivoBp
import app.financepro.core.taxa.mensalPpm
import app.financepro.core.time.monthRange
import app.financepro.data.indices.Cdi
import app.financepro.data.indices.IndicesPrefs
import app.financepro.data.indices.buscarCdi
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.MesDeInvestimento
import app.financepro.domain.usecase.previstoDe
import app.financepro.domain.usecase.rendimentoDe
import app.financepro.domain.usecase.saldoAoFimDe
import app.financepro.domain.usecase.serieSomada
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Um investimento na tela. REQ-INV-002 · REQ-INV-004
 *
 * [anualBp] e [previstoCents] são nuláveis pelo mesmo motivo: um papel atrelado
 * ao CDI não tem taxa enquanto o CDI não chegou, e uma conta de investimento
 * criada antes deste módulo não tem indexador nenhum. Zero seria a resposta
 * errada — ele seria lido como "não rende".
 */
data class LinhaDeInvestimento(
    val conta: Account,
    val saldoCents: Long,
    val anualBp: Int?,
    val previstoCents: Long?,
    val lancadoCents: Long,
) {
    val jaLancado: Boolean get() = lancadoCents != 0L
}

/** O rendimento sendo lançado, com o valor sugerido já dentro. REQ-INV-003 */
data class Lancamento(val conta: Account, val cents: Long)

/**
 * O que a tela de investimentos precisa saber. REQ-INV-002 · REQ-INV-004
 *
 * Tudo derivado do trio (contas, transações, mês), como em `ReportsState`:
 * guardar as linhas prontas ao lado obrigaria a recompô-las a cada gravação, e
 * o primeiro esquecimento mostraria o rendimento de um mês com o saldo de outro.
 *
 * [mes] nasce no **mês passado**, e não no corrente: o rendimento de um mês só
 * se conhece quando ele fecha, e quem abre o app no dia 2 está atrás do que
 * aconteceu em agosto. As setas levam ao mês corrente para quem quiser a
 * projeção do que está correndo.
 */
data class InvestmentsState(
    val contas: List<Account> = emptyList(),
    val todas: List<Txn> = emptyList(),
    val cdi: Cdi? = null,
    val mes: YearMonth = YearMonth.now().minusMonths(1),
    val lancando: Lancamento? = null,
    val editandoCdi: Boolean = false,
    val buscandoCdi: Boolean = false,
    val erro: String? = null,
    val carregado: Boolean = false,
) {
    val investimentos: List<Account> by lazy { contas.filter { it.isInvestimento && !it.archived } }

    val linhas: List<LinhaDeInvestimento> by lazy { investimentos.map(::linhaDe) }

    val totalCents: Long by lazy { linhas.sumOf { it.saldoCents } }

    val lancadoNoMesCents: Long by lazy { linhas.sumOf { it.lancadoCents } }

    /** REQ-INV-004 — os 12 meses do gráfico e da lista, somando as contas. */
    val serie: List<MesDeInvestimento> by lazy { serieSomada(todas, investimentos, mes) }

    val vazio: Boolean get() = investimentos.isEmpty()

    private fun linhaDe(conta: Account): LinhaDeInvestimento {
        val anual = conta.indexador?.let { ix ->
            conta.taxaBp?.let { anualEfetivoBp(ix, it, cdi?.anualBp) }
        }
        return LinhaDeInvestimento(
            conta = conta,
            saldoCents = saldoAoFimDe(todas, conta, mes),
            anualBp = anual,
            previstoCents = anual?.let { previstoDe(todas, conta, mes, mensalPpm(it)) },
            lancadoCents = rendimentoDe(todas, conta, mes),
        )
    }
}

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
    contas: AccountRepository,
    private val txns: TxnRepository,
    private val categorias: CategoryRepository,
    private val indices: IndicesPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(InvestmentsState())
    val state: StateFlow<InvestmentsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(contas.observeAll(), txns.observeTudo(), indices.observar()) { cs, ts, cdi ->
                Triple(cs, ts, cdi)
            }.collect { (cs, ts, cdi) ->
                _state.update { it.copy(contas = cs, todas = ts, cdi = cdi, carregado = true) }
            }
        }
    }

    fun mesAnterior() = _state.update { it.copy(mes = it.mes.minusMonths(1)) }

    /**
     * Trava no mês corrente. Adiante dele não há rendimento a lançar, e a data
     * da transação seria recuada para hoje — o lançamento sumiria da tela que
     * o criou, porque o mês exibido não é o mês em que ele caiu.
     */
    fun mesSeguinte() = _state.update { it.copy(mes = minOf(it.mes.plusMonths(1), YearMonth.now())) }

    /** REQ-INV-003 — abre a folha com o previsto dentro, editável. */
    fun lancar(linha: LinhaDeInvestimento) = _state.update {
        it.copy(lancando = Lancamento(linha.conta, linha.previstoCents ?: 0))
    }

    fun alterarLancamento(cents: Long) = _state.update {
        it.copy(lancando = it.lancando?.copy(cents = cents))
    }

    fun fecharLancamento() = _state.update { it.copy(lancando = null, erro = null) }

    /**
     * REQ-INV-003 — o rendimento vira uma transação `INCOME` de verdade.
     *
     * A data é o último dia do mês, **ou hoje se o mês ainda não fechou**: uma
     * transação com data futura entra no saldo do mesmo jeito (ela é `cleared`),
     * e datar em 30/09 um lançamento feito no dia 2 poria no extrato um dia que
     * ainda não aconteceu.
     *
     * A categoria vem de `idDeRendimentos`, que cria a dela na primeira vez —
     * `INCOME` sem categoria é recusado por `validateTxn` (REQ-TXN-005).
     */
    fun confirmarLancamento() {
        val lancamento = _state.value.lancando ?: return
        val mes = _state.value.mes
        if (lancamento.cents <= 0) {
            _state.update { it.copy(erro = "Informe um valor") }
            return
        }
        viewModelScope.launch {
            val data = minOf(monthRange(mes).endInclusive, LocalDate.now())
            txns.salvar(
                Txn(
                    accountId = lancamento.conta.id,
                    type = TxnType.INCOME,
                    amountCents = lancamento.cents,
                    date = data,
                    categoryId = categorias.idDeRendimentos(),
                    // Sem o mês no texto, de propósito: a data da transação já
                    // o diz, e `TxnRepository.salvar` aprende uma regra de
                    // pagador com a descrição (REQ-ACT-001). "Rendimento de
                    // 08/2026" criaria uma regra nova por mês, para sempre;
                    // "Rendimento" cria uma, que ainda por cima serve para
                    // categorizar rendimento vindo de extrato importado.
                    description = "Rendimento",
                ),
            )
            _state.update { it.copy(lancando = null, erro = null) }
        }
    }

    /** REQ-INV-005 — a busca sob toque, além da diária do `CdiWorker`. */
    fun atualizarCdi() {
        if (_state.value.buscandoCdi) return
        _state.update { it.copy(buscandoCdi = true, erro = null) }
        viewModelScope.launch {
            val cdi = buscarCdi()
            if (cdi != null) indices.guardar(cdi)
            _state.update {
                it.copy(
                    buscandoCdi = false,
                    erro = if (cdi == null) "Não deu para falar com o Banco Central agora" else null,
                )
            }
        }
    }

    fun editarCdi(editando: Boolean) = _state.update { it.copy(editandoCdi = editando, erro = null) }

    private companion object {
        /** De 0,01% a 100% ao ano, em pontos-base. */
        val CDI_BP_RANGE = 1..10_000
    }

    /**
     * REQ-INV-006 — o valor à mão, para quem está sem rede.
     *
     * Teto de 100% a.a. porque o erro de digitação previsível é escrever 1490
     * (os pontos-base) onde se pede 14,90 (o por cento). Sem o teto, o app
     * calcularia rendimento sobre um CDI de 1.490% e a pessoa acreditaria — o
     * número não vem de lugar nenhum que a desminta.
     */
    fun informarCdi(anualBp: Int) {
        if (anualBp !in CDI_BP_RANGE) {
            _state.update { it.copy(erro = "Use um CDI entre 0,01% e 100% ao ano") }
            return
        }
        viewModelScope.launch {
            indices.guardar(Cdi(anualBp = anualBp, em = LocalDate.now(), manual = true))
            _state.update { it.copy(editandoCdi = false) }
        }
    }
}
