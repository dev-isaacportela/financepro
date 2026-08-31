package app.financepro.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.toArgb
import app.financepro.core.ui.theme.ConcreteGray
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.balanceOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Contas com saldo, e o formulário de edição. REQ-ACC-001 · REQ-ACC-002 · REQ-ACC-005
 *
 * O saldo de cada linha sai de `balanceOf`, o caso de uso puro da T-008, sobre a
 * mesma lista de transações — não de um `SUM` por conta em `@Query`. Duas fontes
 * para a regra mais sensível do app divergiriam, e a errada seria a que ninguém
 * testou.
 */
data class AccountsState(
    val contas: List<Account> = emptyList(),
    val saldos: Map<Long, Long> = emptyMap(),
    val mostrarArquivadas: Boolean = false,
    /** Conta em edição; `null` fecha o formulário. */
    val editando: Account? = null,
    val erro: String? = null,
) {
    val visiveis: List<Account>
        get() = contas.filter { mostrarArquivadas || !it.archived }

    val arquivadas: Int get() = contas.count { it.archived }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val contas: AccountRepository,
    txns: TxnRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(contas.observeAll(), txns.observeTudo()) { cs, ts -> cs to ts }
                .collect { (cs, ts) ->
                    _state.update { it.copy(contas = cs, saldos = saldos(cs, ts)) }
                }
        }
    }

    fun alternarArquivadas() = _state.update { it.copy(mostrarArquivadas = !it.mostrarArquivadas) }

    fun editar(conta: Account) = _state.update { it.copy(editando = conta, erro = null) }

    fun nova() = _state.update {
        val vazia = Account(
            id = 0,
            name = "",
            type = AccountType.CHECKING,
            colorArgb = ConcreteGray.toArgb(),
            iconKey = "wallet",
        )
        it.copy(editando = vazia, erro = null)
    }

    fun fechar() = _state.update { it.copy(editando = null, erro = null) }

    fun alterar(conta: Account) = _state.update { it.copy(editando = conta, erro = null) }

    /**
     * REQ-ACC-005 — arquivar, nunca excluir.
     *
     * Excluir levaria as transações junto por `CASCADE`, e o relatório do ano
     * passado mudaria sozinho. Arquivada, a conta sai das listas e do saldo
     * total e o histórico fica de pé.
     */
    fun arquivar(conta: Account) = viewModelScope.launch {
        contas.arquivar(conta, !conta.archived)
    }

    fun salvar() {
        val conta = _state.value.editando ?: return
        val erro = validar(conta)
        if (erro != null) {
            _state.update { it.copy(erro = erro) }
            return
        }
        viewModelScope.launch {
            contas.salvar(conta)
            _state.update { it.copy(editando = null, erro = null) }
        }
    }

    /**
     * REQ-ACC-002 — cartão exige limite, fechamento e vencimento.
     *
     * Fica aqui e não em `domain/usecase` porque a regra completa de cartão —
     * faixa de dia, dia 29 a 31, conta de pagamento — é da T-022, com teste
     * próprio. Duplicar metade dela no domínio agora criaria a segunda fonte de
     * verdade que a T-022 teria de reconciliar.
     */
    private fun validar(conta: Account): String? = when {
        conta.name.isBlank() -> "Dê um nome à conta"
        !conta.isCard -> null
        conta.creditLimitCents == null -> "Informe o limite do cartão"
        conta.closingDay == null -> "Informe o dia de fechamento"
        conta.dueDay == null -> "Informe o dia de vencimento"
        else -> null
    }

    private fun saldos(cs: List<Account>, ts: List<Txn>) = cs.associate { it.id to balanceOf(it, ts) }
}
