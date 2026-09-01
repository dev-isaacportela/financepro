package app.financepro.feature.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.core.time.MonthRange
import app.financepro.core.time.monthRange
import app.financepro.data.repo.BudgetRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Budget
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.BudgetProgress
import app.financepro.domain.usecase.budgetProgress
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

/** A folha de teto aberta. `categoriaId` nulo é "ainda escolhendo a categoria". */
data class TetoEmEdicao(val categoriaId: Long? = null, val cents: Long = 0)

/**
 * O que a tela de orçamento precisa saber. REQ-BUD-003 · REQ-BUD-005
 *
 * O progresso é **derivado** a cada leitura, como a fatura (ADR-004): o estado
 * guarda tetos, categorias e transações, e `budgetProgress` compõe. Guardar a
 * lista pronta ao lado obrigaria a recompô-la a cada gravação, e o primeiro
 * esquecimento mostraria a barra de um mês com o teto de outro.
 */
data class BudgetState(
    val mes: YearMonth,
    val hoje: LocalDate = LocalDate.now(),
    val tetos: List<Budget> = emptyList(),
    val categorias: List<Category> = emptyList(),
    val todas: List<Txn> = emptyList(),
    val folha: TetoEmEdicao? = null,
) {
    /**
     * ponytail: `monthStartDay` fixo em 1 até existir tela de ajustes.
     * `monthRange` já aceita o parâmetro (REQ-CORE-003) e `budgetProgress` já o
     * respeita — falta só de onde lê-lo, e é o mesmo `ponytail:` da lista de
     * transações e do resumo do período.
     */
    val periodo: MonthRange get() = monthRange(mes)

    val progresso: List<BudgetProgress>
        get() = budgetProgress(tetos, categorias, todas, periodo, hoje)

    /** Só despesa: orçar receita seria orçar o que não se controla gastando. */
    private val orcaveis: List<Category>
        get() = categorias.filter { it.kind == CategoryKind.EXPENSE && !it.archived }

    /** As que ainda não têm teto **neste** mês. */
    val semTeto: List<Category>
        get() {
            val comTeto = tetos.filter { it.month == mes }.map { it.categoryId }.toSet()
            return orcaveis.filter { it.id !in comTeto }
        }

    /** REQ-BUD-005 — só faz sentido oferecer a cópia se há o que copiar. */
    val temMesAnterior: Boolean get() = tetos.any { it.month == mes.minusMonths(1) }

    fun categoriaDe(id: Long?): Category? = categorias.firstOrNull { it.id == id }

    val categoriaDaFolha: Category? get() = categoriaDe(folha?.categoriaId)
}

@HiltViewModel
class BudgetViewModel @Inject constructor(
    categorias: CategoryRepository,
    txns: TxnRepository,
    private val tetos: BudgetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetState(mes = YearMonth.now()))
    val state: StateFlow<BudgetState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                categorias.observeActive(),
                txns.observeTudo(),
                tetos.observeAll(),
            ) { cats, ts, bs -> Triple(cats, ts, bs) }
                .collect { (cats, ts, bs) ->
                    _state.update { it.copy(categorias = cats, todas = ts, tetos = bs) }
                }
        }
    }

    fun mesAnterior() = _state.update { it.copy(mes = it.mes.minusMonths(1)) }

    fun mesSeguinte() = _state.update { it.copy(mes = it.mes.plusMonths(1)) }

    /** [categoriaId] nulo abre a folha na escolha de categoria. */
    fun abrirTeto(categoriaId: Long?) = _state.update { atual ->
        val cents = atual.tetos.firstOrNull { it.categoryId == categoriaId && it.month == atual.mes }
        atual.copy(folha = TetoEmEdicao(categoriaId, cents?.limitCents ?: 0))
    }

    fun escolherCategoria(id: Long) = _state.update {
        it.copy(folha = it.folha?.copy(categoriaId = id))
    }

    fun valor(cents: Long) = _state.update { it.copy(folha = it.folha?.copy(cents = cents)) }

    fun fecharTeto() = _state.update { it.copy(folha = null) }

    /**
     * Grava o teto. REQ-BUD-001
     *
     * Valor zerado é tratado como remoção, e não recusado: o repositório já diz
     * que teto zero não é teto, e mandar o usuário procurar outro botão para
     * dizer a mesma coisa seria transformar a regra numa pegadinha.
     */
    fun salvarTeto() {
        val atual = _state.value
        val categoria = atual.folha?.categoriaId ?: return
        val cents = atual.folha.cents
        viewModelScope.launch {
            if (cents > 0) tetos.definir(categoria, atual.mes, cents) else tetos.remover(categoria, atual.mes)
            _state.update { it.copy(folha = null) }
        }
    }

    fun removerTeto() {
        val atual = _state.value
        val categoria = atual.folha?.categoriaId ?: return
        viewModelScope.launch {
            tetos.remover(categoria, atual.mes)
            _state.update { it.copy(folha = null) }
        }
    }

    /** REQ-BUD-005 — em uma ação. */
    fun copiarDoMesAnterior() = viewModelScope.launch {
        tetos.copiarDoMesAnterior(_state.value.mes)
    }
}
