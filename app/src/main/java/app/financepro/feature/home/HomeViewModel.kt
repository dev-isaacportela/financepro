package app.financepro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.usecase.totalBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    contas: AccountRepository,
    txns: TxnRepository,
) : ViewModel() {

    /**
     * O saldo vem de `totalBalance`, o mesmo caso de uso puro da T-008 — não de
     * um `SUM` em `@Query`. Duas fontes para a regra mais sensível do app
     * divergiriam, e a que estaria certa seria a que ninguém testou.
     */
    val saldoCents = combine(contas.observeActive(), txns.observeBetween(INICIO, FIM)) { cs, ts ->
        totalBalance(cs, ts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), 0L)

    private companion object {
        const val PARADA_MS = 5_000L

        // ponytail: janela fixa e larga em vez de "todos os períodos". Trocar por
        // uma query sem intervalo quando a T-014 definir os filtros de período.
        val INICIO: LocalDate = LocalDate.of(2000, 1, 1)
        val FIM: LocalDate = LocalDate.of(2100, 1, 1)
    }
}
