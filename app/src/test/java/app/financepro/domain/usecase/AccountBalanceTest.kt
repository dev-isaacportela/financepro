package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** REQ-ACC-003 · REQ-ACC-004 · REQ-ACC-007 · REQ-TXN-006 · REQ-CARD-009 */
@Req("REQ-ACC-003", "REQ-ACC-004", "REQ-ACC-007", "REQ-TXN-006", "REQ-CARD-009")
class AccountBalanceTest {

    private val hoje = LocalDate.of(2026, 8, 31)

    private val corrente = Account(1, "Corrente", AccountType.CHECKING, initialBalanceCents = 10000)
    private val carteira = Account(2, "Carteira", AccountType.CASH)
    private val cartao = Account(
        3, "Nubank", AccountType.CREDIT_CARD,
        creditLimitCents = 500000, closingDay = 10, dueDay = 20, paymentAccountId = 1,
    )
    private val arquivada = Account(4, "Antiga", AccountType.SAVINGS, 90000, archived = true)

    private fun despesa(conta: Long, cents: Long, cleared: Boolean = true) =
        Txn(accountId = conta, type = TxnType.EXPENSE, amountCents = -cents, date = hoje, cleared = cleared)

    private fun transferencia(de: Long, para: Long, cents: Long) =
        Txn(
            accountId = de, counterAccountId = para, type = TxnType.TRANSFER,
            amountCents = -cents, date = hoje,
        )

    /** As quatro linhas da tabela do requisito. */
    @Test
    fun `tabela de saldo`() {
        // Conta abre com R$ 100, despesa de R$ 30 -> R$ 70.
        assertEquals(7000L, balanceOf(corrente, listOf(despesa(1, 3000))))

        // Transferência de R$ 50 de A para B: A cai 50, B sobe 50.
        val t = listOf(transferencia(de = 1, para = 2, cents = 5000))
        assertEquals(5000L, balanceOf(corrente, t))
        assertEquals(5000L, balanceOf(carteira, t))

        // Previsto não altera o saldo.
        assertEquals(10000L, balanceOf(corrente, listOf(despesa(1, 3000, cleared = false))))
    }

    @Test
    fun `transferencia nao cria nem destroi dinheiro`() {
        // O invariante do Art. 7. Se esta soma mudar, o app inventou ou perdeu
        // dinheiro do usuário — é o pior defeito possível no produto.
        val contas = listOf(corrente, carteira)
        val antes = contas.sumOf { balanceOf(it, emptyList()) }

        listOf(1L, 7L, 5000L, 999_999L).forEach { valor ->
            val t = listOf(transferencia(de = 1, para = 2, cents = valor))
            assertEquals("valor=$valor", antes, contas.sumOf { balanceOf(it, t) })
        }
    }

    @Test
    fun `transferencia de volta restaura os saldos`() {
        val t = listOf(
            transferencia(de = 1, para = 2, cents = 3000),
            transferencia(de = 2, para = 1, cents = 3000),
        )
        assertEquals(10000L, balanceOf(corrente, t))
        assertEquals(0L, balanceOf(carteira, t))
    }

    @Test
    fun `saldo de abertura entra no calculo`() {
        assertEquals(10000L, balanceOf(corrente, emptyList()))
        assertEquals(0L, balanceOf(carteira, emptyList()))
    }

    @Test
    fun `saldo total exclui cartoes e arquivadas`() {
        // Misturar a dívida do cartão faria o usuário achar que tem mais
        // dinheiro do que tem. É o erro clássico da categoria.
        val contas = listOf(corrente, carteira, cartao, arquivada)
        val txns = listOf(despesa(3, 20000))   // compra no cartão

        assertEquals(10000L, totalBalance(contas, txns))
        assertEquals(20000L, cardDebt(contas, txns))
    }

    @Test
    fun `saldo do cartao usa a mesma formula, sem excecao`() {
        // REQ-CARD-009: negativo quando se deve.
        val txns = listOf(despesa(3, 20000))
        assertEquals(-20000L, balanceOf(cartao, txns))
    }

    @Test
    fun `pagar fatura abate a divida sem codigo especial de cartao`() {
        // O pagamento é só uma TRANSFER da corrente para o cartão. Nenhum ramo
        // condicional em lugar nenhum — é o retorno da decisão do ADR-003.
        val txns = listOf(
            despesa(3, 20000),                                  // compra
            transferencia(de = 1, para = 3, cents = 20000),     // pagamento
        )
        assertEquals(0L, balanceOf(cartao, txns))
        assertEquals(-10000L, balanceOf(corrente, txns))
    }

    @Test
    fun `pagamento parcial abate so o que foi pago`() {
        val txns = listOf(
            despesa(3, 20000),
            transferencia(de = 1, para = 3, cents = 12000),
        )
        assertEquals(-8000L, balanceOf(cartao, txns))
    }

    @Test
    fun `transacao de outra conta nao contamina o saldo`() {
        val txns = listOf(despesa(2, 5000))
        assertEquals(10000L, balanceOf(corrente, txns))
        assertEquals(-5000L, balanceOf(carteira, txns))
    }
}
