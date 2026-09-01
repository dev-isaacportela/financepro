package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * REQ-CARD-005 · REQ-CARD-008 — o que entra na fatura, e o limite que sobra.
 *
 * O cartão fecha dia 10 e vence dia 20, então a fatura de março vai de 11 de
 * fevereiro a 10 de março — a janela que a T-023 já provou em `InvoiceMonthTest`
 * e que aqui só é usada.
 */
@Req("REQ-CARD-005", "REQ-CARD-008")
class InvoiceTest {

    private val cartao = UMA_CONTA.copy(
        id = 7,
        name = "Nubank",
        type = AccountType.CREDIT_CARD,
        creditLimitCents = 5_000_00,
        closingDay = 10,
        dueDay = 20,
    )
    private val corrente = UMA_CONTA.copy(id = 1, name = "Corrente")
    private val marco = YearMonth.of(2026, 3)
    private val hoje = LocalDate.of(2026, 3, 5)

    private fun compra(dia: LocalDate, cents: Long, conta: Long = cartao.id) = Txn(
        accountId = conta,
        type = TxnType.EXPENSE,
        amountCents = -cents,
        date = dia,
        categoryId = 10,
    )

    private fun pagamento(dia: LocalDate, cents: Long) = Txn(
        accountId = corrente.id,
        type = TxnType.TRANSFER,
        amountCents = -cents,
        date = dia,
        counterAccountId = cartao.id,
    )

    @Test
    fun `pagamento de fatura nao entra na fatura`() {
        // O item que mais custa quando falta: pagamento é uma TRANSFER **para** o
        // cartão, e contá-lo como item faria o pagamento aumentar a conta que ele
        // quita. O total tem de ser só a compra.
        val txns = listOf(
            compra(LocalDate.of(2026, 3, 2), 300_00),
            pagamento(LocalDate.of(2026, 2, 20), 1_000_00),
        )

        val fatura = invoiceFor(cartao, txns, marco, hoje)

        assertEquals(1, fatura.items.size)
        assertEquals(300_00L, fatura.totalCents)
    }

    @Test
    fun `compra depois do fechamento cai na fatura seguinte`() {
        val txns = listOf(
            compra(LocalDate.of(2026, 2, 11), 100_00),
            compra(LocalDate.of(2026, 3, 10), 200_00),
            compra(LocalDate.of(2026, 3, 11), 400_00),
        )

        // 11/fev a 10/mar compõem março; 11/mar já é abril.
        assertEquals(300_00L, invoiceFor(cartao, txns, marco, hoje).totalCents)
        assertEquals(400_00L, invoiceFor(cartao, txns, YearMonth.of(2026, 4), hoje).totalCents)
    }

    @Test
    fun `compra de outra conta nao entra`() {
        val txns = listOf(
            compra(LocalDate.of(2026, 3, 2), 300_00),
            compra(LocalDate.of(2026, 3, 2), 999_00, conta = corrente.id),
        )

        assertEquals(300_00L, invoiceFor(cartao, txns, marco, hoje).totalCents)
    }

    @Test
    fun `estorno no cartao abate o total pela mesma soma`() {
        val txns = listOf(
            compra(LocalDate.of(2026, 3, 2), 300_00),
            Txn(
                accountId = cartao.id,
                type = TxnType.INCOME,
                amountCents = 50_00,
                date = LocalDate.of(2026, 3, 3),
                categoryId = 20,
            ),
        )

        assertEquals(250_00L, invoiceFor(cartao, txns, marco, hoje).totalCents)
    }

    @Test
    fun `limite disponivel desconta parcela futura ja lancada`() {
        // REQ-CARD-008 — a dívida é tudo que está lançado, não só a fatura do mês.
        // As três parcelas de dezembro compradas hoje já ocuparam o limite.
        val txns = listOf(
            compra(LocalDate.of(2026, 3, 2), 300_00),
            compra(LocalDate.of(2026, 4, 2), 300_00),
            compra(LocalDate.of(2026, 5, 2), 300_00),
        )

        assertEquals(5_000_00L - 900_00L, availableLimitFor(cartao, txns))
    }

    @Test
    fun `pagamento devolve limite sem nenhum codigo de cartao`() {
        val txns = listOf(
            compra(LocalDate.of(2026, 3, 2), 300_00),
            pagamento(LocalDate.of(2026, 3, 21), 300_00),
        )

        // O segundo termo de `balanceOf` (ADR-003) é quem devolve o limite: a
        // saída de −300 na corrente, invertida, é +300 no cartão.
        assertEquals(5_000_00L, availableLimitFor(cartao, txns))
    }
}
