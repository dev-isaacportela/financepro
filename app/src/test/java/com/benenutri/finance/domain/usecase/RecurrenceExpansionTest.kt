package com.benenutri.finance.domain.usecase

import com.benenutri.finance.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** REQ-REC-001, REQ-REC-002 e REQ-REC-006 — datas de lançamento recorrente. */
@Req("REQ-REC-001", "REQ-REC-002", "REQ-REC-006")
class RecurrenceExpansionTest {

    private fun d(ano: Int, mes: Int, dia: Int) = LocalDate.of(ano, mes, dia)

    private fun datas(spec: RecurrenceSpec, quantas: Int) =
        occurrences(spec).take(quantas).toList()

    // ---------- REQ-REC-006: clamp de dia do mês ----------

    /** As quatro linhas da tabela do requisito, literalmente. */
    @Test
    fun `tabela de clamp`() {
        fun mensal(dia: Int, ano: Int, mes: Int) = occurrences(
            RecurrenceSpec(
                frequency = Frequency.MONTHLY,
                startDate = d(ano, mes, 1),
                dayOfMonth = dia,
            ),
        ).first()

        assertEquals(d(2026, 2, 28), mensal(31, 2026, 2))
        assertEquals(d(2028, 2, 29), mensal(31, 2028, 2))   // bissexto
        assertEquals(d(2026, 4, 30), mensal(31, 2026, 4))
        assertEquals(d(2026, 2, 28), mensal(30, 2026, 2))
    }

    @Test
    fun `o clamp nao gruda`() {
        // O erro clássico: calcular cada ocorrência a partir da anterior. Aí
        // 31/01 vira 28/02 e fica preso em 28 para sempre — o usuário perde o
        // lançamento do dia certo em todos os meses seguintes.
        val plano = datas(
            RecurrenceSpec(Frequency.MONTHLY, startDate = d(2026, 1, 31)),
            5,
        )
        assertEquals(
            listOf(
                d(2026, 1, 31),
                d(2026, 2, 28),
                d(2026, 3, 31),   // voltou para 31
                d(2026, 4, 30),
                d(2026, 5, 31),
            ),
            plano,
        )
    }

    @Test
    fun `29 de fevereiro anual cai em 28 nos anos comuns`() {
        val plano = datas(
            RecurrenceSpec(Frequency.YEARLY, startDate = d(2028, 2, 29)),
            5,
        )
        assertEquals(
            listOf(
                d(2028, 2, 29),
                d(2029, 2, 28),
                d(2030, 2, 28),
                d(2031, 2, 28),
                d(2032, 2, 29),   // bissexto de novo
            ),
            plano,
        )
    }

    // ---------- REQ-REC-002: frequências e interval ----------

    @Test
    fun `diaria com interval`() {
        assertEquals(
            listOf(d(2026, 3, 1), d(2026, 3, 2), d(2026, 3, 3)),
            datas(RecurrenceSpec(Frequency.DAILY, d(2026, 3, 1)), 3),
        )
        assertEquals(
            listOf(d(2026, 3, 1), d(2026, 3, 11), d(2026, 3, 21), d(2026, 3, 31)),
            datas(RecurrenceSpec(Frequency.DAILY, d(2026, 3, 1), interval = 10), 4),
        )
    }

    @Test
    fun `semanal com interval`() {
        // 2026-03-02 é uma segunda-feira.
        val inicio = d(2026, 3, 2)
        assertEquals(DayOfWeek.MONDAY, inicio.dayOfWeek)
        assertEquals(
            listOf(d(2026, 3, 2), d(2026, 3, 9), d(2026, 3, 16)),
            datas(RecurrenceSpec(Frequency.WEEKLY, inicio), 3),
        )
        assertEquals(
            listOf(d(2026, 3, 2), d(2026, 3, 16), d(2026, 3, 30)),
            datas(RecurrenceSpec(Frequency.WEEKLY, inicio, interval = 2), 3),
        )
    }

    @Test
    fun `semanal com dia da semana diferente do inicio avanca ate ele`() {
        // Começa numa segunda, quer toda sexta: a primeira ocorrência é a
        // sexta da mesma semana, não a da semana seguinte.
        val plano = datas(
            RecurrenceSpec(
                Frequency.WEEKLY,
                startDate = d(2026, 3, 2),
                weekday = DayOfWeek.FRIDAY,
            ),
            3,
        )
        assertEquals(listOf(d(2026, 3, 6), d(2026, 3, 13), d(2026, 3, 20)), plano)
        assertTrue(plano.all { it.dayOfWeek == DayOfWeek.FRIDAY })
    }

    @Test
    fun `mensal com interval`() {
        assertEquals(
            listOf(d(2026, 1, 10), d(2026, 4, 10), d(2026, 7, 10)),
            datas(RecurrenceSpec(Frequency.MONTHLY, d(2026, 1, 10), interval = 3), 3),
        )
    }

    @Test
    fun `anual com interval`() {
        assertEquals(
            listOf(d(2026, 5, 20), d(2028, 5, 20), d(2030, 5, 20)),
            datas(RecurrenceSpec(Frequency.YEARLY, d(2026, 5, 20), interval = 2), 3),
        )
    }

    @Test
    fun `anual com mes explicito`() {
        val plano = datas(
            RecurrenceSpec(
                Frequency.YEARLY,
                startDate = d(2026, 1, 5),
                monthOfYear = 12,
                dayOfMonth = 25,
            ),
            3,
        )
        assertEquals(listOf(d(2026, 12, 25), d(2027, 12, 25), d(2028, 12, 25)), plano)
    }

    // ---------- fronteiras ----------

    @Test
    fun `nenhuma ocorrencia antes do inicio`() {
        // Regra do dia 5 começando no dia 20: a de março já passou, então a
        // primeira é a de abril.
        val plano = datas(
            RecurrenceSpec(Frequency.MONTHLY, startDate = d(2026, 3, 20), dayOfMonth = 5),
            2,
        )
        assertEquals(listOf(d(2026, 4, 5), d(2026, 5, 5)), plano)
    }

    @Test
    fun `endDate encerra a sequencia`() {
        val plano = occurrences(
            RecurrenceSpec(
                Frequency.MONTHLY,
                startDate = d(2026, 1, 10),
                endDate = d(2026, 4, 10),
            ),
        ).toList()
        assertEquals(
            listOf(d(2026, 1, 10), d(2026, 2, 10), d(2026, 3, 10), d(2026, 4, 10)),
            plano,
        )
    }

    @Test
    fun `endDate no mesmo dia do inicio gera uma unica ocorrencia`() {
        val plano = occurrences(
            RecurrenceSpec(
                Frequency.DAILY,
                startDate = d(2026, 1, 10),
                endDate = d(2026, 1, 10),
            ),
        ).toList()
        assertEquals(listOf(d(2026, 1, 10)), plano)
    }

    @Test
    fun `sequencia e estritamente crescente em todas as frequencias`() {
        // Data repetida geraria lançamento duplicado; data que retrocede
        // quebraria a idempotência da T-031, que avança lastGeneratedDate.
        Frequency.entries.forEach { freq ->
            listOf(1, 2, 3).forEach { passo ->
                val plano = datas(
                    RecurrenceSpec(freq, d(2026, 1, 31), interval = passo),
                    30,
                )
                assertEquals("$freq interval=$passo", 30, plano.size)
                plano.zipWithNext { a, b ->
                    assertTrue("$freq interval=$passo: $a -> $b", b > a)
                }
            }
        }
    }

    @Test
    fun `entrada invalida e recusada`() {
        listOf(0, -1).forEach { i ->
            runCatching { RecurrenceSpec(Frequency.DAILY, d(2026, 1, 1), interval = i) }
                .onSuccess { org.junit.Assert.fail("aceitou interval=$i") }
                .onFailure { assertTrue(it is IllegalArgumentException) }
        }
        runCatching {
            RecurrenceSpec(Frequency.MONTHLY, d(2026, 1, 1), dayOfMonth = 32)
        }.onSuccess { org.junit.Assert.fail("aceitou dayOfMonth=32") }
            .onFailure { assertTrue(it is IllegalArgumentException) }

        runCatching {
            RecurrenceSpec(Frequency.DAILY, d(2026, 5, 1), endDate = d(2026, 1, 1))
        }.onSuccess { org.junit.Assert.fail("aceitou endDate antes de startDate") }
            .onFailure { assertTrue(it is IllegalArgumentException) }
    }
}
