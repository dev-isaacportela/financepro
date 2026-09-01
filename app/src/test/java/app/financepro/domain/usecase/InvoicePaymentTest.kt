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
 * REQ-CARD-006 — pagar fatura é uma transferência, e mais nada.
 *
 * O aceite da spec é o teste inteiro: depois do pagamento integral, a dívida
 * daquela fatura fica zerada **sem nenhum tratamento especial de cartão** no
 * cálculo de saldo. Se `balanceOf` precisasse de um `if (isCard)`, este teste
 * passaria mesmo assim — por isso ele mede pelo saldo e pelo limite, que são as
 * duas funções que um caso especial teria de contaminar.
 */
@Req("REQ-CARD-006")
class InvoicePaymentTest {

    private val corrente = UMA_CONTA.copy(id = 1, name = "Corrente", initialBalanceCents = 2_000_00)
    private val cartao = UMA_CONTA.copy(
        id = 7,
        name = "Nubank",
        type = AccountType.CREDIT_CARD,
        creditLimitCents = 5_000_00,
        closingDay = 10,
        dueDay = 20,
        paymentAccountId = corrente.id,
    )
    private val marco = YearMonth.of(2026, 3)
    private val compra = Txn(
        accountId = cartao.id,
        type = TxnType.EXPENSE,
        amountCents = -300_00,
        date = LocalDate.of(2026, 3, 2),
        categoryId = 10,
    )

    private fun fatura(txns: List<Txn>, hoje: LocalDate = LocalDate.of(2026, 3, 25)) =
        invoiceFor(cartao, txns, marco, hoje)

    @Test
    fun `pagamento sai da conta de pagamento para o cartao, no vencimento`() {
        val pagamento = cardPaymentFor(cartao, fatura(listOf(compra)))

        assertEquals(TxnType.TRANSFER, pagamento.type)
        assertEquals(corrente.id, pagamento.accountId)
        assertEquals(cartao.id, pagamento.counterAccountId)
        // Negativo na origem: é o que sai dela (REQ-TXN-002).
        assertEquals(-300_00L, pagamento.amountCents)
        // Data no vencimento, não hoje: é a data que o extrato do banco mostra.
        assertEquals(LocalDate.of(2026, 3, 20), pagamento.date)
    }

    @Test
    fun `pagamento integral zera a divida da fatura sem codigo especial de cartao`() {
        val pagamento = cardPaymentFor(cartao, fatura(listOf(compra)))
        val depois = listOf(compra, pagamento)

        assertEquals(InvoiceStatus.PAGA, fatura(depois).status)
        // O aceite da spec, medido onde um caso especial apareceria: o saldo do
        // cartão volta a zero pela fórmula do ADR-003, sem exceção (REQ-CARD-009).
        assertEquals(0L, balanceOf(cartao, depois))
        assertEquals(5_000_00L, availableLimitFor(cartao, depois))
        // E o dinheiro saiu mesmo de onde devia sair.
        assertEquals(1_700_00L, balanceOf(corrente, depois))
    }

    @Test
    fun `pagamento parcial abate o que foi pago e a fatura segue fechada`() {
        val parcial = cardPaymentFor(cartao, fatura(listOf(compra)), amountCents = 100_00)
        val depois = listOf(compra, parcial)

        assertEquals(InvoiceStatus.FECHADA, fatura(depois).status)
        assertEquals(100_00L, fatura(depois).paidCents)
        assertEquals(-200_00L, balanceOf(cartao, depois))
    }

    @Test
    fun `o padrao e o que falta, nao o total`() {
        // Encontrado no aparelho: com o total, pagar R$ 100 de R$ 300 e voltar
        // para quitar ofereceria R$ 300 de novo, e dois toques pagariam R$ 400
        // numa fatura de R$ 300. REQ-CARD-006 foi corrigida junto (Art. 3).
        val parcial = cardPaymentFor(cartao, fatura(listOf(compra)), amountCents = 100_00)
        val depois = listOf(compra, parcial)

        assertEquals(200_00L, fatura(depois).restanteCents)
        assertEquals(-200_00L, cardPaymentFor(cartao, fatura(depois)).amountCents)

        // E os dois pagamentos juntos quitam exatamente a fatura, sem sobra —
        // soma das partes igual ao todo (Art. 7).
        val quitada = depois + cardPaymentFor(cartao, fatura(depois))
        assertEquals(0L, fatura(quitada).restanteCents)
        assertEquals(0L, balanceOf(cartao, quitada))
    }

    @Test
    fun `restante nunca fica negativo`() {
        val demais = cardPaymentFor(cartao, fatura(listOf(compra)), amountCents = 400_00)

        // Pagar a mais deixa crédito no cartão; "falta −R$ 100" seria a tela
        // pedindo ao usuário para interpretar um sinal em vez de ler um número.
        assertEquals(0L, fatura(listOf(compra, demais)).restanteCents)
    }

    @Test
    fun `pagar a mais nao quebra nada, e sobra como credito no cartao`() {
        val demais = cardPaymentFor(cartao, fatura(listOf(compra)), amountCents = 400_00)
        val depois = listOf(compra, demais)

        assertEquals(InvoiceStatus.PAGA, fatura(depois).status)
        // Saldo positivo no cartão é crédito a favor, e o limite disponível
        // passa do limite — que é o que o banco também faz.
        assertEquals(100_00L, balanceOf(cartao, depois))
        assertEquals(5_100_00L, availableLimitFor(cartao, depois))
    }
}
