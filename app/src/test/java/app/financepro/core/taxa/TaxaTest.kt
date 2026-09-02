package app.financepro.core.taxa

import app.financepro.core.testing.Req
import app.financepro.domain.model.Indexador
import app.financepro.domain.usecase.rendimentoPrevisto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-INV-001 · REQ-INV-002 */
@Req("REQ-INV-001", "REQ-INV-002")
class TaxaTest {

    @Test
    fun `prefixado rende a propria taxa`() {
        assertEquals(1250, anualEfetivoBp(Indexador.PREFIXADO, 1250, cdiAnualBp = 1490))
    }

    @Test
    fun `prefixado nao depende do CDI existir`() {
        // É a diferença entre os dois indexadores: o papel prefixado rende o que
        // promete mesmo com o aparelho em modo avião desde a instalação.
        assertEquals(1250, anualEfetivoBp(Indexador.PREFIXADO, 1250, cdiAnualBp = null))
    }

    @Test
    fun `cento e dez por cento do CDI a catorze noventa da dezesseis trinta e nove`() {
        assertEquals(1639, anualEfetivoBp(Indexador.CDI, taxaBp = 11_000, cdiAnualBp = 1490))
    }

    @Test
    fun `sem CDI, investimento atrelado a ele nao tem taxa`() {
        // Nulo e não zero: zero seria "rende nada", e a tela mostraria um
        // previsto de R$ 0,00 como se fosse fato. Nulo vira travessão.
        assertNull(anualEfetivoBp(Indexador.CDI, taxaBp = 11_000, cdiAnualBp = null))
    }

    @Test
    fun `taxa mensal e composta, nao a anual dividida por doze`() {
        // 14,90% a.a. dão 1,1642% a.m. O ingênuo daria 1,2416% — meio ponto
        // percentual de erro no ano, que é o tamanho exato do desencontro entre
        // o previsto e o extrato que este teste existe para impedir.
        assertEquals(11_642, mensalPpm(1490))
        assertTrue(mensalPpm(1490) < 1490 * 100 / 12)
    }

    @Test
    fun `taxa zero rende zero`() {
        assertEquals(0, mensalPpm(0))
    }

    @Test
    fun `doze meses compostos voltam a taxa anual`() {
        // O invariante do arquivo: se `mensalPpm` errar a escala ou o sinal,
        // é aqui que aparece. Cem mil reais a 14,90% a.a. rendem R$ 14.900,00.
        val principal = 100_000_00L
        val mensal = mensalPpm(1490)

        var saldo = principal
        repeat(12) { saldo += rendimentoPrevisto(saldo, mensal) }

        val rendeu = saldo - principal
        val esperado = 14_900_00L
        // Tolerância de um real em cem mil: é o acúmulo do arredondamento a
        // centavo, doze vezes, mais o meio ppm de `mensalPpm`. Apertar mais
        // seria testar o arredondamento, não a taxa.
        assertTrue("rendeu $rendeu, esperava ~$esperado", Math.abs(rendeu - esperado) <= 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `taxa anual negativa nao passa`() {
        mensalPpm(-100)
    }
}
