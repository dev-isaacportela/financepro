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
import app.financepro.domain.usecase.totalBalance
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
) {
    /**
     * Vem de `totalBalance`, o caso de uso puro da T-008 — nunca de um `SUM` em
     * `@Query`. Duas fontes para a regra mais sensível do app divergiriam, e a
     * que estaria certa seria a que ninguém testou.
     */
    val saldoCents: Long get() = totalBalance(contas, txns)

    val cartoes: List<Account> get() = contas.filter { it.isCard && !it.archived }

    /** Positivo para exibição: é dívida, e o bloco já diz isso por escrito. */
    val dividaCents: Long get() = cardDebt(contas, txns)

    val comparativo: Comparativo get() = comparativoDe(txns, mes)

    /**
     * As últimas por **data e depois id**: dois lançamentos do mesmo dia saem na
     * ordem em que foram criados, que é a que a pessoa acabou de ver acontecer.
     */
    val ultimas: List<Txn>
        get() = txns.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id })
            .take(ULTIMAS)

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
    txns: TxnRepository,
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
}
