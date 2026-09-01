package app.financepro.data.ingest

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-IMP-008 · REQ-ACT-004 — a tabela do requisito, linha por linha.
 *
 * Errar aqui não aparece na tela: aparece como uma transação que sumiu na
 * importação, ou como o extrato duplicado que o dedupe deveria ter pego. As
 * quatro linhas da spec são exatamente os quatro jeitos de errar.
 */
@Req("REQ-IMP-008", "REQ-ACT-004")
class NormalizeTest {

    private val dia = LocalDate.of(2026, 8, 15)

    private fun mesmaChave(a: String, b: String) =
        dedupeKey(1, dia, -1_000, a) == dedupeKey(1, dia, -1_000, b)

    // ---------- a tabela de REQ-IMP-008 ----------

    @Test
    fun `caixa alta e baixa dao a mesma chave`() {
        assertEquals("SUPERMERCADO XYZ", normalize("Supermercado Xyz"))
        assertTrue(mesmaChave("SUPERMERCADO XYZ", "Supermercado Xyz"))
    }

    @Test
    fun `NSU diferente na mesma padaria da a mesma chave`() {
        assertEquals("PADARIA", normalize("PADARIA 00123456"))
        assertTrue(mesmaChave("PADARIA 00123456", "PADARIA 00987654"))
    }

    @Test
    fun `espacos repetidos colapsam`() {
        assertEquals("UBER TRIP", normalize("UBER   TRIP"))
        assertTrue(mesmaChave("UBER   TRIP", "UBER TRIP"))
    }

    @Test
    fun `estabelecimentos diferentes nao colidem`() {
        assertNotEquals(normalize("MERCADO A"), normalize("MERCADO B"))
        assertTrue(!mesmaChave("MERCADO A", "MERCADO B"))
    }

    // ---------- o que a tabela não diz, e que erra sozinho ----------

    @Test
    fun `acento sai, inclusive o cedilha`() {
        // A tabela de pares esqueceria o Ç na primeira revisão, e "AÇOUGUE" com
        // e sem cedilha viraria dois estabelecimentos.
        assertEquals("ACOUGUE SAO JOAO", normalize("Açougue São João"))
        assertTrue(mesmaChave("Açougue São João", "ACOUGUE SAO JOAO"))
    }

    @Test
    fun `numero curto fica, senao lojas com numero no nome colidem`() {
        assertEquals("POSTO 24H", normalize("Posto 24h"))
        assertNotEquals(normalize("POSTO 24H"), normalize("POSTO 12H"))
    }

    @Test
    fun `pontuacao vira espaco, e nao some colando palavras`() {
        // "MERCADO-SUL" colado viraria "MERCADOSUL", que não casa com
        // "MERCADO SUL" do mesmo estabelecimento noutro extrato.
        assertEquals("MERCADO SUL", normalize("MERCADO-SUL"))
        assertEquals("PAG SUPERMERCADO", normalize("PAG*SUPERMERCADO"))
    }

    @Test
    fun `descricao so de digitos vira vazio, e ainda assim tem chave`() {
        assertEquals("", normalize("00123456"))
        // A chave continua distinguindo pelo resto: conta, dia e valor.
        assertNotEquals(dedupeKey(1, dia, -1_000, "00123456"), dedupeKey(2, dia, -1_000, "00123456"))
    }

    // ---------- a chave ----------

    @Test
    fun `a chave muda com conta, dia e valor`() {
        val base = dedupeKey(1, dia, -1_000, "PADARIA")

        assertNotEquals(base, dedupeKey(2, dia, -1_000, "PADARIA"))
        assertNotEquals(base, dedupeKey(1, dia.plusDays(1), -1_000, "PADARIA"))
        assertNotEquals(base, dedupeKey(1, dia, -1_001, "PADARIA"))
    }

    @Test
    fun `a chave e estavel entre execucoes`() {
        // Um hash que mudasse a cada execução faria a segunda importação do
        // mesmo arquivo passar inteira como novidade.
        assertEquals(dedupeKey(1, dia, -1_000, "PADARIA"), dedupeKey(1, dia, -1_000, "PADARIA"))
    }

    @Test
    fun `a chave traz o prefixo que a separa da do OFX`() {
        val chave = dedupeKey(1, dia, -1_000, "PADARIA")

        assertTrue(chave, chave.startsWith("h:"))
        // 64 hexadecimais de SHA-256, mais o prefixo.
        assertEquals("h:".length + 64, chave.length)
    }
}
