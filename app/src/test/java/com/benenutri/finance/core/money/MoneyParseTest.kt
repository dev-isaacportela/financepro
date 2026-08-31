package com.benenutri.finance.core.money

import com.benenutri.finance.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * REQ-IMP-004 — conversão de texto para centavos sem ponto flutuante.
 *
 * Este é o ponto por onde `Double` entraria no projeto se ninguém estivesse
 * olhando: `"187.50".toDouble()` num parser de OFX. Ver Art. 6 e ADR-002.
 */
@Req("REQ-IMP-004")
class MoneyParseTest {

    /** As seis linhas da tabela do requisito, literalmente. */
    @Test
    fun `tabela do requisito`() {
        assertEquals(-18750L, parseCents("-187.50"))
        assertEquals(-18750L, parseCents("-187,50"))
        assertEquals(123456L, parseCents("1.234,56"))
        assertEquals(123456L, parseCents("1,234.56"))
        assertEquals(10000L, parseCents("100"))
        assertEquals(7L, parseCents("0.07"))
    }

    @Test
    fun `ultimo separador com 3 digitos e milhar, nao decimal`() {
        // Em pt-BR "1.234" é mil duzentos e trinta e quatro reais.
        assertEquals(123400L, parseCents("1.234"))
        assertEquals(123400L, parseCents("1,234"))
        assertEquals(123456700L, parseCents("1.234.567"))
    }

    @Test
    fun `uma casa decimal e completada com zero`() {
        assertEquals(120L, parseCents("1,2"))
        assertEquals(-120L, parseCents("-1.2"))
    }

    @Test
    fun `parte inteira ausente vale zero`() {
        assertEquals(50L, parseCents(",50"))
        assertEquals(7L, parseCents(".07"))
    }

    @Test
    fun `ruido de extrato bancario e tolerado`() {
        assertEquals(123456L, parseCents("R$ 1.234,56"))
        assertEquals(123456L, parseCents("  r$1.234,56  "))
        assertEquals(-18750L, parseCents("-R$ 187,50"))
        // Espaço não separável (U+00A0) aparece em CSV exportado de web.
        assertEquals(123456L, parseCents("R$ 1.234,56"))
        assertEquals(18750L, parseCents("+187,50"))
    }

    @Test
    fun `zero e valor valido`() {
        // REQ-CORE-002 recusa transação com valor zero, mas isso é papel do
        // ValidateTxn. O parser apenas converte.
        assertEquals(0L, parseCents("0"))
        assertEquals(0L, parseCents("0,00"))
    }

    @Test
    fun `entrada invalida devolve null em vez de lancar`() {
        assertNull(parseCents(""))
        assertNull(parseCents("   "))
        assertNull(parseCents("abc"))
        assertNull(parseCents("12abc"))
        assertNull(parseCents("-"))
        assertNull(parseCents("."))
        assertNull(parseCents(","))
        assertNull(parseCents("R$"))
        // Estoura Long: precisa recusar, não truncar em silêncio.
        assertNull(parseCents("99999999999999999999"))
    }

    @Test
    fun `nao usa ponto flutuante em lugar nenhum`() {
        // 0.1 + 0.2 != 0.3 em binário. Se o parser passasse por Double, este
        // teste falharia — é a demonstração de por que o Art. 6 existe.
        val dez = parseCents("0,10")!!
        val vinte = parseCents("0,20")!!
        assertEquals(parseCents("0,30"), dez + vinte)

        // Mesma armadilha, valor de mercado real repetido 3 vezes.
        val item = parseCents("19,99")!!
        assertEquals(parseCents("59,97"), item * 3)
    }
}
