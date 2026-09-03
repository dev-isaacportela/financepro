package app.financepro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.Comparativo
import app.financepro.domain.usecase.cardDebt
import app.financepro.domain.usecase.comparativoDe
import app.financepro.domain.usecase.proximasContas
import app.financepro.domain.usecase.totalBalance
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
 * O que o dashboard precisa saber. REQ-UI-004 · REQ-UI-006
 *
 * Um único estado, tudo derivado dele (arquitetura.md §3, regra 3) — mesma
 * escolha de [app.financepro.feature.txn.TransactionsState]. Guardar saldo,
 * dívida e comparativo em campos paralelos obrigaria a recalcular os três
 * juntos a cada chegada do banco, e o primeiro esquecimento mostraria o saldo
 * de agora com o comparativo de antes.
 *
 * [txns] é o histórico inteiro porque saldo é de todos os tempos
 * (REQ-ACC-003); o recorte por período acontece dentro de [comparativoDe].
 */
data class HomeState(
    val contas: List<Account> = emptyList(),
    val categorias: List<Category> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val mes: YearMonth = YearMonth.now(),
    val hoje: LocalDate = LocalDate.now(),
) {
    /**
     * Vem de `totalBalance`, o caso de uso puro da T-008 — nunca de um `SUM` em
     * `@Query`. Duas fontes para a regra mais sensível do app divergiriam, e a
     * que estaria certa seria a que ninguém testou.
     */
    val saldoCents: Long by lazy { totalBalance(contas, txns) }

    val cartoes: List<Account> by lazy { contas.filter { it.isCard && !it.archived } }

    /** Positivo para exibição: é dívida, e o bloco já diz isso por escrito. */
    val dividaCents: Long by lazy { cardDebt(contas, txns) }

    val comparativo: Comparativo by lazy { comparativoDe(txns, mes) }

    /**
     * As últimas por **data e depois id**: dois lançamentos do mesmo dia saem na
     * ordem em que foram criados, que é a que a pessoa acabou de ver acontecer.
     */
    val ultimas: List<Txn> by lazy {
        txns.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id })
            .take(ULTIMAS)
    }

    /**
     * REQ-REC-008 — o que vence nos próximos 7 dias e ainda não foi efetivado.
     *
     * A regra é a mesma função pura que a spec nomeia, e não um filtro escrito
     * aqui: o bloco do dashboard e qualquer outro lugar que venha a mostrar
     * previstas precisam concordar sobre o que é "próxima conta".
     */
    val proximas: List<Txn> by lazy { proximasContas(txns, hoje) }

    val vazio: Boolean get() = txns.isEmpty()

    fun contaDe(id: Long?): Account? = contas.firstOrNull { it.id == id }

    fun categoriaDe(id: Long?): Category? = categorias.firstOrNull { it.id == id }

    private companion object {
        /** Cabem na dobra sem rolagem no aparelho de referência; o resto é a aba Transações. */
        const val ULTIMAS = 5
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    contas: AccountRepository,
    categorias: CategoryRepository,
    private val txns: TxnRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                contas.observeAll(),
                categorias.observeActive(),
                txns.observeTudo(),
            ) { cs, cats, ts -> Triple(cs, cats, ts) }
                .collect { (cs, cats, ts) ->
                    _state.update { it.copy(contas = cs, categorias = cats, txns = ts) }
                }
        }
    }

    /**
     * Efetiva uma conta prevista. REQ-REC-008 · REQ-TXN-006
     *
     * Só o `cleared`: a linha já existe, com valor, data, conta e categoria
     * decididos quando foi gerada. Confirmar não é relançar — e passar pelo
     * `salvar` de sempre é o que preserva `dedupeKey`, `recurringRuleId` e o
     * `createdAt` original, que o repositório protege desde a T-050.
     */
    fun efetivar(txn: Txn) = viewModelScope.launch {
        txns.salvar(txn.copy(cleared = true))
    }
}
