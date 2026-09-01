package app.financepro.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.core.time.MonthRange
import app.financepro.core.time.monthRange
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.GrupoDeCategoria
import app.financepro.domain.usecase.PontoMensal
import app.financepro.domain.usecase.despesasPorCategoria
import app.financepro.domain.usecase.evolucaoMensal
import app.financepro.domain.usecase.maioresDespesas
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
 * O que a tela de relatórios precisa saber. REQ-RPT-001 · REQ-RPT-002 · REQ-RPT-003
 *
 * Os três relatórios são **derivados** do mesmo par (transações, mês), como a
 * fatura e o orçamento: guardar as fatias prontas ao lado obrigaria a
 * recompô-las a cada gravação, e o primeiro esquecimento mostraria a pizza de
 * um mês com a lista de outro.
 *
 * [todas] é o histórico inteiro, e não a janela: a evolução precisa de doze
 * meses para trás, e recortar antes deixaria o gráfico com um mês só.
 */
data class ReportsState(
    val mes: YearMonth,
    val categorias: List<Category> = emptyList(),
    val todas: List<Txn> = emptyList(),
) {
    /**
     * ponytail: `monthStartDay` fixo em 1 até existir tela de ajustes — o mesmo
     * da lista, do orçamento e do dashboard. `monthRange` já aceita o parâmetro
     * (REQ-CORE-003); falta só de onde lê-lo.
     */
    val periodo: MonthRange get() = monthRange(mes)

    /** REQ-RPT-001 */
    val fatias: List<GrupoDeCategoria> get() = despesasPorCategoria(todas, periodo)

    /** REQ-RPT-002 */
    val evolucao: List<PontoMensal> get() = evolucaoMensal(todas, mes)

    /** REQ-RPT-003 */
    val maiores: List<Txn> get() = maioresDespesas(todas, periodo)

    /** O denominador dos percentuais da legenda. Zero quando não há despesa. */
    val totalCents: Long get() = fatias.sumOf { it.totalCents }

    val vazio: Boolean get() = fatias.isEmpty()

    fun categoriaDe(id: Long?): Category? = categorias.firstOrNull { it.id == id }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    categorias: CategoryRepository,
    txns: TxnRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsState(mes = YearMonth.now()))
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(categorias.observeActive(), txns.observeTudo()) { cats, ts -> cats to ts }
                .collect { (cats, ts) ->
                    _state.update { it.copy(categorias = cats, todas = ts) }
                }
        }
    }

    fun mesAnterior() = _state.update { it.copy(mes = it.mes.minusMonths(1)) }

    fun mesSeguinte() = _state.update { it.copy(mes = it.mes.plusMonths(1)) }
}
