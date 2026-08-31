package com.benenutri.finance.core.money

import com.benenutri.finance.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Test

/** REQ-CORE-005 — formatação pt-BR, com sinal explícito na despesa. */
@Req("REQ-CORE-005")
class MoneyFormatTest {

    @Test
    fun `separador de milhar e ponto, decimal e virgula`() {
        assertEquals("R\$ 1.234,56", formatBRL(123456))
        assertEquals("R\$ 12.345,67", formatBRL(1234567))
        assertEquals("R\$ 1.234.567,89", formatBRL(123456789))
    }

    @Test
    fun `despesa leva sinal de menos explicito`() {
        assertEquals("−R\$ 18,50", formatBRL(-1850))
        assertEquals("−R\$ 1.234,56", formatBRL(-123456))
    }

    @Test
    fun `sinal e o menos tipografico, nao o hifen`() {
        // U+2212 MINUS SIGN. O hífen (U+002D) tem largura menor e desalinha
        // uma coluna de valores, que é justamente o que `tnum` tenta resolver.
        val negativo = formatBRL(-1850)
        assertEquals('−', negativo.first())
    }

    @Test
    fun `valores abaixo de um real preservam as duas casas`() {
        assertEquals("R\$ 0,00", formatBRL(0))
        assertEquals("R\$ 0,07", formatBRL(7))
        assertEquals("R\$ 0,70", formatBRL(70))
        assertEquals("−R\$ 0,07", formatBRL(-7))
    }

    @Test
    fun `extremos de Long nao estouram`() {
        // A formatação parte do texto dos dígitos justamente para não precisar
        // negar: `-Long.MIN_VALUE` estoura silenciosamente.
        assertEquals("−R\$ 92.233.720.368.547.758,08", formatBRL(Long.MIN_VALUE))
        assertEquals("R\$ 92.233.720.368.547.758,07", formatBRL(Long.MAX_VALUE))
    }

    @Test
    fun `formatar e reinterpretar devolve o mesmo valor`() {
        listOf(0L, 7L, 1850L, -1850L, 123456L, -123456789L).forEach { cents ->
            assertEquals("round-trip de $cents", cents, parseCents(formatBRL(cents)))
        }
    }
}
