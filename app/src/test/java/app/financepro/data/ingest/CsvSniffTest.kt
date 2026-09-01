package app.financepro.data.ingest

import app.financepro.core.money.parseCents
import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-IMP-006 — separador, formato de data e decimal, em três amostras.
 *
 * O caso que faz o farejador ganhar o seu lugar é o extrato brasileiro típico:
 * separador `;` e decimal `,`, onde contar vírgulas escolheria o separador
 * errado e quebraria o valor de toda linha ao meio.
 *
 * As amostras são sintéticas, montadas sobre os três dialetos que circulam. Vale
 * aqui a mesma ressalva da T-037: arquivo real de banco entra na T-041.
 */
@Req("REQ-IMP-006")
class CsvSniffTest {

    private fun arquivo(nome: String, charset: java.nio.charset.Charset = Charsets.UTF_8): String =
        checkNotNull(javaClass.getResourceAsStream("/csv/$nome")) { "fixture ausente: $nome" }
            .use { String(it.readBytes(), charset) }

    // ---------- banco A: `;` + dd/MM/yyyy + vírgula + cabeçalho ----------

    @Test
    fun `ponto e virgula ganha da virgula do decimal`() {
        val formato = farejarCsv(arquivo("banco-a-ponto-e-virgula.csv"))

        // Contando vírgulas, este arquivo pareceria ter duas colunas — e
        // "-187,50" viraria "-187" e "50".
        assertEquals(';', formato.separador)
        assertEquals(',', formato.decimal)
        assertEquals(PadraoDeData.DIA_MES_ANO, formato.data)
        assertTrue(formato.temCabecalho)
    }

    @Test
    fun `o BOM da exportacao nao vira parte da primeira celula`() {
        // O CSV que o próprio app exporta começa com BOM (REQ-BAK-001), e
        // reimportá-lo é o caminho mais provável de um usuário testando.
        val tabela = lerCsv(arquivo("banco-a-ponto-e-virgula.csv"), ';')

        assertEquals("Data", tabela.first().first())
    }

    @Test
    fun `as linhas viram valor e data de verdade`() {
        val texto = arquivo("banco-a-ponto-e-virgula.csv")
        val formato = farejarCsv(texto)
        val linhas = lerCsv(texto, formato.separador).drop(if (formato.temCabecalho) 1 else 0)

        assertEquals(3, linhas.size)
        assertEquals(LocalDate.of(2026, 8, 1), parseDataCsv(linhas[0][0], formato.data))
        assertEquals(-187_50L, parseCents(linhas[0][2]))
        // "5.000,00" — ponto de milhar e vírgula decimal na mesma célula.
        assertEquals(5_000_00L, parseCents(linhas[1][2]))
    }

    // ---------- banco B: `,` + yyyy-MM-dd + ponto + cabeçalho ----------

    @Test
    fun `virgula com data ISO e decimal ponto`() {
        val formato = farejarCsv(arquivo("banco-b-virgula.csv"))

        assertEquals(',', formato.separador)
        assertEquals('.', formato.decimal)
        assertEquals(PadraoDeData.ANO_MES_DIA, formato.data)
        assertTrue(formato.temCabecalho)
    }

    @Test
    fun `data ISO vira LocalDate`() {
        val texto = arquivo("banco-b-virgula.csv")
        val formato = farejarCsv(texto)
        val primeira = lerCsv(texto, formato.separador)[1]

        assertEquals(LocalDate.of(2026, 8, 10), parseDataCsv(primeira[0], formato.data))
        assertEquals(-89_90L, parseCents(primeira[2]))
    }

    // ---------- banco C: sem cabeçalho, aspas com separador dentro ----------

    @Test
    fun `arquivo sem cabecalho e reconhecido como tal`() {
        val formato = farejarCsv(arquivo("banco-c-sem-cabecalho.csv", Charsets.ISO_8859_1))

        assertEquals(';', formato.separador)
        assertEquals(PadraoDeData.DIA_MES_ANO, formato.data)
        // A primeira linha já é transação: tratá-la como cabeçalho perderia uma.
        assertFalse(formato.temCabecalho)
    }

    @Test
    fun `separador dentro de aspas nao abre coluna`() {
        val tabela = lerCsv(arquivo("banco-c-sem-cabecalho.csv", Charsets.ISO_8859_1), ';')

        assertEquals(3, tabela.first().size)
        assertEquals("ALUGUEL; PARCELA 1", tabela.first()[1])
    }

    @Test
    fun `aspa dobrada vira uma aspa`() {
        val tabela = lerCsv(arquivo("banco-c-sem-cabecalho.csv", Charsets.ISO_8859_1), ';')

        assertEquals("MERCADO \"DO ZE\"", tabela[1][1])
    }

    // ---------- bordas ----------

    @Test
    fun `linha vazia no fim nao vira transacao fantasma`() {
        // Arquivo de banco termina com quebra de linha, sempre.
        val tabela = lerCsv("01/08/2026;PADARIA;-10,00\r\n\r\n", ';')

        assertEquals(1, tabela.size)
    }

    @Test
    fun `arquivo que nao e tabela nao inventa formato de data`() {
        val formato = farejarCsv("isto aqui é um texto qualquer\ne outra linha\n")

        assertNull(formato.data)
        // Sem data em lugar nenhum, a primeira linha "é cabeçalho" — e a tela de
        // mapeamento tem `data == null` para saber que precisa pedir ajuda.
        assertTrue(formato.temCabecalho)
    }

    @Test
    fun `tabulacao tambem e separador`() {
        val formato = farejarCsv("01/08/2026\tPADARIA\t-10,00\n02/08/2026\tMERCADO\t-20,00\n")

        assertEquals('\t', formato.separador)
    }

    @Test
    fun `quebra de linha dentro de aspas nao parte a transacao`() {
        val tabela = lerCsv("01/08/2026;\"PADARIA\nCENTRAL\";-10,00\n", ';')

        assertEquals(1, tabela.size)
        assertEquals("PADARIA\nCENTRAL", tabela.single()[1])
    }
}
