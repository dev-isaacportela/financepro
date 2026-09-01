package app.financepro.feature.card

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.GrupoDeCategoria
import app.financepro.domain.usecase.Invoice
import app.financepro.domain.usecase.agruparPorCategoria
import app.financepro.domain.usecase.availableLimitFor
import app.financepro.domain.usecase.cardPaymentFor
import app.financepro.domain.usecase.invoiceFor
import app.financepro.feature.Cartao
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
 * O que a tela de cartão precisa saber. REQ-CARD-005 · REQ-CARD-006 · REQ-CARD-008
 *
 * A fatura é **derivada** aqui, como em todo lugar (ADR-004): o estado guarda as
 * transações e o mês, e `invoiceFor` compõe a cada leitura. Guardar a fatura
 * pronta ao lado obrigaria a recompô-la em toda gravação, e o primeiro
 * esquecimento mostraria o total de um mês com os itens de outro.
 *
 * [hoje] é campo, e não `LocalDate.now()` no meio do cálculo, porque o status da
 * fatura depende dele — assim o teste escolhe o dia em vez de torcer.
 */
data class CardState(
    val mes: YearMonth,
    val hoje: LocalDate = LocalDate.now(),
    val cartao: Account? = null,
    val contas: List<Account> = emptyList(),
    val categorias: List<Category> = emptyList(),
    val todas: List<Txn> = emptyList(),
    /** Centavos digitados na folha de pagamento. `null` com a folha fechada. */
    val pagando: Long? = null,
) {
    val fatura: Invoice? get() = cartao?.let { invoiceFor(it, todas, mes, hoje) }

    val grupos: List<GrupoDeCategoria> get() = agruparPorCategoria(fatura?.items.orEmpty())

    val limiteDisponivelCents: Long? get() = cartao?.let { availableLimitFor(it, todas) }

    /** A conta que quita, quando o cartão tem uma configurada (REQ-CARD-001). */
    val contaDePagamento: Account?
        get() = contas.firstOrNull { it.id == cartao?.paymentAccountId }

    fun categoriaDe(id: Long?): Category? = categorias.firstOrNull { it.id == id }
}

@HiltViewModel
class CardViewModel @Inject constructor(
    contas: AccountRepository,
    categorias: CategoryRepository,
    private val txns: TxnRepository,
    estado: SavedStateHandle,
) : ViewModel() {

    private val cartaoId = estado.toRoute<Cartao>().id

    private val _state = MutableStateFlow(CardState(mes = YearMonth.now()))
    val state: StateFlow<CardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // `observeAll`: um cartão arquivado sai das listas de seleção, mas a
            // fatura dele continua existindo — e chegar aqui por um link antigo
            // não pode devolver tela vazia (REQ-ACC-005).
            combine(
                contas.observeAll(),
                categorias.observeActive(),
                txns.observeTudo(),
            ) { cs, cats, ts -> Triple(cs, cats, ts) }
                .collect { (cs, cats, ts) ->
                    _state.update {
                        it.copy(
                            cartao = cs.firstOrNull { c -> c.id == cartaoId },
                            contas = cs,
                            categorias = cats,
                            todas = ts,
                        )
                    }
                }
        }
    }

    fun mesAnterior() = _state.update { it.copy(mes = it.mes.minusMonths(1)) }

    fun mesSeguinte() = _state.update { it.copy(mes = it.mes.plusMonths(1)) }

    /** REQ-CARD-006 — abre com o que **falta**, não com o total. Ver `restanteCents`. */
    fun abrirPagamento() = _state.update { it.copy(pagando = it.fatura?.restanteCents ?: 0) }

    fun valorDoPagamento(cents: Long) = _state.update { it.copy(pagando = cents) }

    fun fecharPagamento() = _state.update { it.copy(pagando = null) }

    /**
     * Grava o pagamento. REQ-CARD-006
     *
     * A transferência sai de `cardPaymentFor`, no domínio: quem paga o quê, em
     * que direção e em que data é regra, e regra não mora em ViewModel (Art. 9).
     */
    fun pagar() {
        val atual = _state.value
        val cartao = atual.cartao
        val fatura = atual.fatura
        val valor = atual.pagando ?: 0
        // Uma guarda só, e cedo: sem cartão, sem fatura, sem conta de pagamento
        // ou com valor zerado não há transferência que faça sentido — e gravar
        // uma de zero centavos violaria REQ-CORE-002 na hora de validar.
        if (cartao?.paymentAccountId == null || fatura == null || valor <= 0) return
        viewModelScope.launch {
            txns.salvar(cardPaymentFor(cartao, fatura, valor))
            _state.update { it.copy(pagando = null) }
        }
    }
}
