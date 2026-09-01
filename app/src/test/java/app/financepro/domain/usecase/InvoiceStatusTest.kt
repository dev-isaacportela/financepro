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
 * REQ-CARD-007 — as três linhas da tabela de status, e nada em coluna.
 *
 * A fatura de março fecha em 10/03 e vence em 20/03.
 */
@Req("REQ-CARD-007")
class InvoiceStatusTest {

    private val cartao = UMA_CONTA.copy(
        id = 7,
        name = "Nubank",
        type = AccountType.CREDIT_CARD,
        creditLimitCents = 5_000_00,
        closingDay = 10,
        dueDay = 20,
    )
    private val marco = YearMonth.of(2026, 3)

    private val compra = Txn(
        accountId = cartao.id,
        type = TxnType.EXPENSE,
        amountCents = -300_00,
        date = LocalDate.of(2026, 3, 2),
        categoryId = 10,
    )

    private fun pagamento(dia: LocalDate, cents: Long) = Txn(
        accountId = 1,
        type = TxnType.TRANSFER,
        amountCents = -cents,
        date = dia,
        counterAccountId = cartao.id,
    )

    private fun statusEm(hoje: LocalDate, txns: List<Txn> = listOf(compra)) =
        invoiceFor(cartao, txns, marco, hoje).status

    @Test
    fun `aberta ate o dia do fechamento, inclusive`() {
        assertEquals(InvoiceStatus.ABERTA, statusEm(LocalDate.of(2026, 3, 9)))
        // A borda: a spec diz "hoje ≤ data de fechamento". No dia 10 ainda dá
        // para comprar e cair nesta fatura, então ela não pode estar fechada.
        assertEquals(InvoiceStatus.ABERTA, statusEm(LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `fechada quando passou o fechamento e o pagamento nao cobre`() {
        assertEquals(InvoiceStatus.FECHADA, statusEm(LocalDate.of(2026, 3, 11)))
        assertEquals(
            InvoiceStatus.FECHADA,
            statusEm(LocalDate.of(2026, 3, 25), listOf(compra, pagamento(LocalDate.of(2026, 3, 20), 299_99))),
        )
    }

    @Test
    fun `paga quando o pagamento desde o fechamento cobre o total`() {
        assertEquals(
            InvoiceStatus.PAGA,
            statusEm(LocalDate.of(2026, 3, 25), listOf(compra, pagamento(LocalDate.of(2026, 3, 20), 300_00))),
        )
    }

    @Test
    fun `pagamento da fatura anterior nao paga esta`() {
        // "Pagamentos desde o fechamento" sem limite superior faria toda fatura
        // antiga virar paga com o tempo. 09/03 é anterior ao fechamento de
        // 10/03, então quita a fatura de fevereiro, não a de março.
        val quitaFevereiro = pagamento(LocalDate.of(2026, 3, 9), 5_000_00)

        assertEquals(InvoiceStatus.FECHADA, statusEm(LocalDate.of(2026, 3, 11), listOf(compra, quitaFevereiro)))
        assertEquals(0L, invoiceFor(cartao, listOf(compra, quitaFevereiro), marco, LocalDate.of(2026, 3, 11)).paidCents)
    }

    @Test
    fun `pagamento do mes seguinte tambem nao paga esta`() {
        // 11/04 já passou o fechamento de abril: quita a fatura de abril.
        val quitaAbril = pagamento(LocalDate.of(2026, 4, 11), 5_000_00)

        assertEquals(InvoiceStatus.FECHADA, statusEm(LocalDate.of(2026, 4, 15), listOf(compra, quitaAbril)))
    }

    @Test
    fun `fatura vazia depois do fechamento nao tem o que dever`() {
        assertEquals(InvoiceStatus.PAGA, statusEm(LocalDate.of(2026, 3, 11), emptyList()))
    }
}
