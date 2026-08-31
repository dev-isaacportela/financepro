package com.benenutri.finance.core.time

import com.benenutri.finance.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** REQ-CORE-003 — um único conceito de mês, derivado de `monthStartDay`. */
@Req("REQ-CORE-003")
class MonthRangeTest {

    private fun range(ano: Int, mes: Int, dia: Int) =
        monthRange(YearMonth.of(ano, mes), dia)

    /** As três linhas da tabela do requisito, literalmente. */
    @Test
    fun `tabela do requisito`() {
        range(2026, 8, 1).let {
            assertEquals(LocalDate.of(2026, 8, 1), it.start)
            assertEquals(LocalDate.of(2026, 8, 31), it.endInclusive)
        }
        range(2026, 8, 5).let {
            assertEquals(LocalDate.of(2026, 8, 5), it.start)
            assertEquals(LocalDate.of(2026, 9, 4), it.endInclusive)
        }
        range(2026, 2, 31).let {
            assertEquals(LocalDate.of(2026, 2, 28), it.start)
            assertEquals(LocalDate.of(2026, 3, 30), it.endInclusive)
        }
    }

    @Test
    fun `dia 31 cai no ultimo dia de cada mes curto`() {
        assertEquals(LocalDate.of(2026, 4, 30), range(2026, 4, 31).start)
        assertEquals(LocalDate.of(2026, 2, 28), range(2026, 2, 31).start)
        // Bissexto: fevereiro tem 29.
        assertEquals(LocalDate.of(2028, 2, 29), range(2028, 2, 31).start)
    }

    @Test
    fun `virada de ano`() {
        range(2026, 12, 5).let {
            assertEquals(LocalDate.of(2026, 12, 5), it.start)
            assertEquals(LocalDate.of(2027, 1, 4), it.endInclusive)
        }
    }

    @Test
    fun `periodos consecutivos nao deixam buraco nem sobrepoem`() {
        // Todo dia do calendário pertence a exatamente um período. Sem isso,
        // uma transação sumiria do orçamento ou seria contada duas vezes.
        listOf(1, 5, 15, 28, 31).forEach { dia ->
            var mes = YearMonth.of(2026, 1)
            repeat(24) {
                val atual = monthRange(mes, dia)
                val proximo = monthRange(mes.plusMonths(1), dia)
                assertEquals(
                    "dia=$dia mes=$mes: fim + 1 deve ser o início do próximo",
                    proximo.start,
                    atual.endInclusive.plusDays(1),
                )
                mes = mes.plusMonths(1)
            }
        }
    }

    @Test
    fun `contains delimita o periodo pelas duas pontas`() {
        val agosto = range(2026, 8, 5)
        assertTrue(LocalDate.of(2026, 8, 5) in agosto)
        assertTrue(LocalDate.of(2026, 9, 4) in agosto)
        assertFalse(LocalDate.of(2026, 8, 4) in agosto)
        assertFalse(LocalDate.of(2026, 9, 5) in agosto)
    }

    @Test
    fun `tamanho do periodo`() {
        assertEquals(31, range(2026, 8, 1).lengthInDays)
        assertEquals(28, range(2026, 2, 1).lengthInDays)
        assertEquals(29, range(2028, 2, 1).lengthInDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dia fora de 1 a 31 e recusado`() {
        range(2026, 8, 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dia zero e recusado`() {
        range(2026, 8, 0)
    }
}
