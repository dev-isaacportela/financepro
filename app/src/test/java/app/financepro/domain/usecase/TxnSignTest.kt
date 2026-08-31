package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-TXN-002 — convenção de sinal.
 *
 * `amountCents` é sempre o efeito líquido na conta `accountId`.
 */
@Req("REQ-TXN-002")
class TxnSignTest {

    private val hoje = LocalDate.of(2026, 8, 31)
    private val conta = UMA_CONTA.copy(id = 1, name = "Corrente")

    private fun txn(tipo: TxnType, cents: Long) =
        Txn(accountId = 1, type = tipo, amountCents = cents, date = hoje, categoryId = 1)

    @Test
    fun `receita e positiva e soma ao saldo`() {
        val t = txn(TxnType.INCOME, 450000)
        assertTrue(t.amountCents > 0)
        assertEquals(450000L, balanceOf(conta, listOf(t)))
    }

    @Test
    fun `despesa e negativa e subtrai do saldo`() {
        val t = txn(TxnType.EXPENSE, -18750)
        assertTrue(t.amountCents < 0)
        assertEquals(-18750L, balanceOf(conta, listOf(t)))
    }

    @Test
    fun `transferencia sai negativa da origem`() {
        val t = Txn(
            accountId = 1, counterAccountId = 2, type = TxnType.TRANSFER,
            amountCents = -100000, date = hoje,
        )
        assertTrue(t.amountCents < 0)
        assertEquals(-100000L, balanceOf(conta, listOf(t)))
    }

    @Test
    fun `o sinal manda, nao o tipo`() {
        // O saldo soma amountCents sem olhar o tipo. É o que mantém a fórmula
        // com um único caminho, sem ramo por tipo de transação.
        val movimentos = listOf(
            txn(TxnType.INCOME, 450000),
            txn(TxnType.EXPENSE, -18750),
            txn(TxnType.EXPENSE, -3000),
        )
        assertEquals(450000L - 18750L - 3000L, balanceOf(conta, movimentos))
    }
}
