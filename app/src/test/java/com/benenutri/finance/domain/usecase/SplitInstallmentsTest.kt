package com.benenutri.finance.domain.usecase

import com.benenutri.finance.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-TXN-007 e REQ-TXN-008 — parcelamento.
 *
 * O teste central é o invariante do Art. 7: **a soma das partes é exatamente o
 * todo**. Se algum dia sobrar tempo para dois testes no projeto inteiro, este é
 * um deles.
 */
@Req("REQ-TXN-007", "REQ-TXN-008")
class SplitInstallmentsTest {

    private val compra = LocalDate.of(2026, 3, 15)

    private fun valores(total: Long, n: Int) =
        splitInstallments(total, n, compra).map { it.amountCents }

    /** As quatro linhas da tabela do requisito, literalmente. */
    @Test
    fun `tabela do requisito`() {
        assertEquals(List(6) { 8571L } + 8574L, valores(60000, 7))
        assertEquals(listOf(3L, 3L, 4L), valores(10, 3))
        assertEquals(listOf(100000L), valores(100000, 1))
        assertEquals(listOf(0L, 1L), valores(1, 2))
    }

    @Test
    fun `soma das parcelas e exatamente o total, para todo n de 1 a 72`() {
        // O invariante do Art. 7. Totais escolhidos para forçar sobra: primos,
        // valores minúsculos, e uma compra grande de verdade.
        val totais = listOf(
            1L, 7L, 10L, 99L, 100L, 999L, 1000L,
            60000L, 100000L, 123457L, 999_999_999L,
        )
        for (total in totais) {
            for (n in INSTALLMENT_RANGE) {
                val parcelas = valores(total, n)
                assertEquals("total=$total n=$n", n, parcelas.size)
                assertEquals("total=$total n=$n", total, parcelas.sum())
            }
        }
    }

    @Test
    fun `funciona igual para despesa, que e negativa`() {
        // Despesa é gravada com sinal negativo (REQ-TXN-002). A divisão inteira
        // do Kotlin trunca em direção a zero, então a sobra continua na última.
        for (n in INSTALLMENT_RANGE) {
            val parcelas = valores(-60000, n)
            assertEquals("n=$n", -60000L, parcelas.sum())
            assertTrue("n=$n: parcela positiva numa despesa", parcelas.all { it <= 0 })
        }
        assertEquals(List(6) { -8571L } + -8574L, valores(-60000, 7))
    }

    @Test
    fun `a sobra vai na ultima parcela, nunca na primeira`() {
        // Quem confere o extrato compara a PRIMEIRA parcela com o valor
        // anunciado na compra. Sobra na primeira faria o número não bater.
        val parcelas = valores(10000, 3)
        assertEquals(3333L, parcelas.first())
        assertEquals(3334L, parcelas.last())
        assertEquals(listOf(3333L, 3333L, 3334L), parcelas)
    }

    @Test
    fun `todas as parcelas menos a ultima sao iguais`() {
        for (n in 2..72) {
            val parcelas = valores(123457, n)
            val cabeca = parcelas.dropLast(1).toSet()
            assertEquals("n=$n: parcelas do meio divergiram", 1, cabeca.size)
        }
    }

    @Test
    fun `indices sao 1-based e o rotulo e legivel`() {
        val plano = splitInstallments(60000, 10, compra)
        assertEquals(1, plano.first().index)
        assertEquals(10, plano.last().index)
        assertEquals("1 de 10", plano.first().label)
        assertEquals("3 de 10", plano[2].label)
        assertTrue(plano.all { it.count == 10 })
    }

    @Test
    fun `datas avancam um mes por parcela`() {
        val plano = splitInstallments(60000, 4, LocalDate.of(2026, 3, 15))
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 6, 15),
            ),
            plano.map { it.date },
        )
    }

    @Test
    fun `compra no fim do mes ajusta em fevereiro`() {
        val plano = splitInstallments(30000, 3, LocalDate.of(2026, 1, 31))
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),   // fevereiro não tem 31
                LocalDate.of(2026, 3, 31),   // e volta para 31, não fica preso em 28
            ),
            plano.map { it.date },
        )
    }

    @Test
    fun `parcelamento atravessa a virada de ano`() {
        val plano = splitInstallments(120000, 12, LocalDate.of(2026, 11, 10))
        assertEquals(LocalDate.of(2026, 11, 10), plano.first().date)
        assertEquals(LocalDate.of(2027, 10, 10), plano.last().date)
    }

    @Test
    fun `entrada invalida e recusada`() {
        listOf(0, -1, 73, 100).forEach { n ->
            runCatching { splitInstallments(60000, n, compra) }
                .onSuccess { org.junit.Assert.fail("aceitou count=$n") }
                .onFailure { assertTrue(it is IllegalArgumentException) }
        }
        runCatching { splitInstallments(0, 3, compra) }
            .onSuccess { org.junit.Assert.fail("aceitou total zero") }
            .onFailure { assertTrue(it is IllegalArgumentException) }
    }
}
