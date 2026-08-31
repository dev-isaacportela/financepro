package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * REQ-CARD-003 e REQ-CARD-004 — competência e vencimento da fatura.
 *
 * A virada de dezembro é o caso que quebra implementação ingênua com aritmética
 * de mês, e por isso tem teste próprio em vez de sair só na tabela.
 */
@Req("REQ-CARD-003", "REQ-CARD-004")
class InvoiceMonthTest {

    private fun dia(ano: Int, mes: Int, d: Int) = LocalDate.of(ano, mes, d)
    private fun mes(ano: Int, m: Int) = YearMonth.of(ano, m)

    // ---------- REQ-CARD-003 ----------

    /** As seis linhas da tabela do requisito, literalmente. */
    @Test
    fun `tabela de competencia`() {
        assertEquals(mes(2026, 3), invoiceMonthFor(dia(2026, 3, 9), closingDay = 10))
        assertEquals(mes(2026, 3), invoiceMonthFor(dia(2026, 3, 10), closingDay = 10))
        assertEquals(mes(2026, 4), invoiceMonthFor(dia(2026, 3, 11), closingDay = 10))
        assertEquals(mes(2027, 1), invoiceMonthFor(dia(2026, 12, 15), closingDay = 10))
        assertEquals(mes(2026, 3), invoiceMonthFor(dia(2026, 3, 1), closingDay = 1))
        assertEquals(mes(2026, 2), invoiceMonthFor(dia(2026, 2, 28), closingDay = 28))
    }

    @Test
    fun `o dia do fechamento pertence a fatura que fecha`() {
        // A fronteira é inclusiva. Errar aqui joga a compra para a fatura
        // seguinte e o usuário paga um mês depois do que esperava.
        (1..28).forEach { fechamento ->
            assertEquals(
                "fechamento=$fechamento",
                mes(2026, 6),
                invoiceMonthFor(dia(2026, 6, fechamento), fechamento),
            )
        }
    }

    @Test
    fun `um dia depois do fechamento ja e a proxima fatura`() {
        (1..27).forEach { fechamento ->
            assertEquals(
                "fechamento=$fechamento",
                mes(2026, 7),
                invoiceMonthFor(dia(2026, 6, fechamento + 1), fechamento),
            )
        }
    }

    @Test
    fun `virada de ano`() {
        // Dezembro + 1 mês tem de virar janeiro do ano seguinte, não mês 13.
        assertEquals(mes(2027, 1), invoiceMonthFor(dia(2026, 12, 11), closingDay = 10))
        assertEquals(mes(2026, 12), invoiceMonthFor(dia(2026, 12, 10), closingDay = 10))
        assertEquals(mes(2027, 1), invoiceMonthFor(dia(2026, 12, 31), closingDay = 28))
    }

    @Test
    fun `fim de mes curto cai na fatura seguinte`() {
        assertEquals(mes(2026, 3), invoiceMonthFor(dia(2026, 2, 28), closingDay = 20))
        // Bissexto: 29 de fevereiro existe e também é depois do fechamento.
        assertEquals(mes(2028, 3), invoiceMonthFor(dia(2028, 2, 29), closingDay = 28))
    }

    @Test
    fun `todo dia do ano pertence a exatamente uma fatura, sem buraco`() {
        // Percorrendo o calendário, a fatura nunca retrocede e nunca pula um
        // mês. Se retrocedesse, uma compra sumiria de uma fatura já paga.
        listOf(1, 5, 10, 20, 28).forEach { fechamento ->
            var data = dia(2026, 1, 1)
            var anterior = invoiceMonthFor(data, fechamento)
            while (data.year <= 2027) {
                val atual = invoiceMonthFor(data, fechamento)
                assertTrue(
                    "fechamento=$fechamento data=$data: fatura retrocedeu",
                    atual >= anterior,
                )
                assertTrue(
                    "fechamento=$fechamento data=$data: pulou uma fatura",
                    atual == anterior || atual == anterior.plusMonths(1),
                )
                anterior = atual
                data = data.plusDays(1)
            }
        }
    }

    // ---------- REQ-CARD-004 ----------

    /** As quatro linhas da tabela do requisito, literalmente. */
    @Test
    fun `tabela de vencimento`() {
        assertEquals(dia(2026, 3, 20), dueDateFor(mes(2026, 3), closingDay = 10, dueDay = 20))
        assertEquals(dia(2026, 4, 10), dueDateFor(mes(2026, 3), closingDay = 20, dueDay = 10))
        assertEquals(dia(2026, 4, 10), dueDateFor(mes(2026, 3), closingDay = 10, dueDay = 10))
        assertEquals(dia(2027, 1, 5), dueDateFor(mes(2026, 12), closingDay = 25, dueDay = 5))
    }

    @Test
    fun `vencimento nunca vem antes do fechamento`() {
        // Invariante do produto: não existe fatura que vence antes de fechar.
        for (fechamento in 1..28) {
            for (vencimento in 1..28) {
                val fatura = mes(2026, 6)
                val fecha = closingDateFor(fatura, fechamento)
                val vence = dueDateFor(fatura, fechamento, vencimento)
                assertTrue(
                    "fechamento=$fechamento vencimento=$vencimento: $vence < $fecha",
                    vence > fecha,
                )
            }
        }
    }

    @Test
    fun `fechamento e vencimento no mesmo dia vence no mes seguinte`() {
        // Um cartão não fecha e vence no mesmo dia.
        assertEquals(dia(2026, 7, 15), dueDateFor(mes(2026, 6), closingDay = 15, dueDay = 15))
    }

    @Test
    fun `virada de ano no vencimento`() {
        assertEquals(dia(2027, 1, 5), dueDateFor(mes(2026, 12), closingDay = 10, dueDay = 5))
        assertEquals(dia(2026, 12, 20), dueDateFor(mes(2026, 12), closingDay = 10, dueDay = 20))
    }

    // ---------- fronteira de entrada ----------

    @Test
    fun `dia fora de 1 a 28 e recusado`() {
        // REQ-CARD-002. A restrição é o que dispensa qualquer clamp aqui.
        listOf(0, 29, 30, 31, -1).forEach { invalido ->
            runCatching { invoiceMonthFor(dia(2026, 3, 10), invalido) }
                .onSuccess { org.junit.Assert.fail("aceitou closingDay=$invalido") }
                .onFailure { assertTrue(it is IllegalArgumentException) }
            runCatching { dueDateFor(mes(2026, 3), 10, invalido) }
                .onSuccess { org.junit.Assert.fail("aceitou dueDay=$invalido") }
                .onFailure { assertTrue(it is IllegalArgumentException) }
        }
    }

    @Test
    fun `nenhum dia valido estoura em fevereiro`() {
        // O motivo de existir a faixa 1..28: todo dia permitido existe em todo
        // mês, então closingDateFor nunca precisa de clamp.
        (1..28).forEach { d ->
            assertEquals(dia(2026, 2, d), closingDateFor(mes(2026, 2), d))
        }
    }
}
