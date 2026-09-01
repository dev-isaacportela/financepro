package app.financepro.data.ingest

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-IMP-005 — a cola entre os parsers e o dedupe.
 *
 * A tela do fluxo é `Teste: manual` na spec, e continua sendo. O que dá para
 * provar sem emulador é o que decide o resultado antes de qualquer pixel: qual
 * formato é o arquivo, qual coluna é qual, e que chave de dedupe cada linha
 * ganha — os três erram em silêncio e só aparecem como transação faltando ou
 * duplicada semanas depois.
 */
@Req("REQ-IMP-005")
class ImportacaoTest {

    private val conta = 1L

    private fun recurso(nome: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(nome)) { "fixture ausente: $nome" }
            .use { it.readBytes() }

    // ---------- qual formato ----------

    @Test
    fun `OFX e reconhecido pela assinatura, nao pela extensao`() {
        // O arquivo pode chegar como .txt: o seletor do sistema devolve
        // `text/plain` e `application/octet-stream` para meio mundo.
        assertEquals(FormatoDeArquivo.OFX, formatoDe(recurso("/ofx/banco-a-1x-cp1252.ofx")))
        assertEquals(FormatoDeArquivo.OFX, formatoDe(recurso("/ofx/banco-b-2x-utf8.ofx")))
    }

    @Test
    fun `CSV e o que nao tem cara de OFX`() {
        assertEquals(FormatoDeArquivo.CSV, formatoDe(recurso("/csv/banco-a-ponto-e-virgula.csv")))
    }

    @Test
    fun `CSV em ISO-8859-1 nao vira losango`() {
        val texto = textoDeCsv(recurso("/csv/banco-c-sem-cabecalho.csv"))

        // CSV não declara charset. Lido como UTF-8, "ZE" com acento viraria o
        // caractere de substituição e o dedupe trataria a loja como outra.
        assertTrue(texto, "MERCADO" in texto)
        assertFalse("sobrou caractere de substituição", '�' in texto)
    }

    // ---------- mapeamento ----------

    private fun tabelaDe(nome: String): Pair<List<List<String>>, CsvFormato> {
        val texto = textoDeCsv(recurso(nome))
        val formato = farejarCsv(texto)
        return lerCsv(texto, formato.separador) to formato
    }

    @Test
    fun `o palpite acerta as tres colunas do extrato com saldo`() {
        val (tabela, formato) = tabelaDe("/csv/banco-a-ponto-e-virgula.csv")

        val mapa = palpiteDeMapeamento(tabela, formato)

        // Data;Historico;Valor;Saldo — a coluna de saldo é numérica também, e o
        // palpite pega a **primeira** numérica depois da data.
        assertEquals(MapeamentoCsv(data = 0, valor = 2, descricao = 1), mapa)
    }

    @Test
    fun `o palpite acerta o arquivo sem cabecalho`() {
        val (tabela, formato) = tabelaDe("/csv/banco-c-sem-cabecalho.csv")

        assertEquals(MapeamentoCsv(data = 0, valor = 2, descricao = 1), palpiteDeMapeamento(tabela, formato))
    }

    @Test
    fun `arquivo que nao e extrato nao rende palpite`() {
        val texto = "isto aqui é um texto qualquer\ne outra linha\n"
        val formato = farejarCsv(texto)

        assertNull(palpiteDeMapeamento(lerCsv(texto, formato.separador), formato))
    }

    @Test
    fun `mapeamento invalido e recusado antes de virar linha errada`() {
        assertFalse("coluna repetida", MapeamentoCsv(0, 0, 1).valido(colunas = 3))
        assertFalse("coluna fora da tabela", MapeamentoCsv(0, 1, 9).valido(colunas = 3))
        assertTrue(MapeamentoCsv(0, 2, 1).valido(colunas = 3))
    }

    @Test
    fun `a assinatura e estavel entre dois extratos do mesmo banco`() {
        val (agosto, _) = tabelaDe("/csv/banco-a-ponto-e-virgula.csv")
        val setembro = listOf(agosto.first()) + listOf(listOf("30/09/2026", "OUTRA", "-1,00", "0,00"))

        // O nome do arquivo muda todo mês; o cabeçalho do banco, não. É por isso
        // que o mapeamento guardado é chaveado pela assinatura.
        assertEquals(assinaturaCsv(agosto), assinaturaCsv(setembro))
    }

    // ---------- candidatas ----------

    @Test
    fun `linhas de CSV viram candidatas com chave de hash`() {
        val (tabela, formato) = tabelaDe("/csv/banco-a-ponto-e-virgula.csv")
        val mapa = checkNotNull(palpiteDeMapeamento(tabela, formato))

        val candidatas = candidatasDoCsv(tabela, formato, mapa, conta)

        assertEquals(3, candidatas.size)
        assertEquals(LocalDate.of(2026, 8, 1), candidatas.first().date)
        assertEquals(-187_50L, candidatas.first().amountCents)
        assertEquals("SUPERMERCADO XYZ LTDA", candidatas.first().description)
        assertTrue(candidatas.all { it.dedupeKey.startsWith("h:") })
    }

    @Test
    fun `cabecalho nao vira transacao`() {
        val (tabela, formato) = tabelaDe("/csv/banco-a-ponto-e-virgula.csv")
        val mapa = checkNotNull(palpiteDeMapeamento(tabela, formato))

        assertEquals(tabela.size - 1, candidatasDoCsv(tabela, formato, mapa, conta).size)
    }

    @Test
    fun `linha de rodape sem data e ignorada, e o resto do arquivo entra`() {
        val texto = "01/08/2026;PADARIA;-10,00\nTotal do período;;-10,00\n"
        val formato = farejarCsv(texto)
        val tabela = lerCsv(texto, formato.separador)

        val candidatas = candidatasDoCsv(tabela, formato, MapeamentoCsv(0, 2, 1), conta)

        assertEquals(listOf("PADARIA"), candidatas.map { it.description })
    }

    @Test
    fun `OFX com FITID usa a chave do banco`() {
        val extrato = parseOfx(recurso("/ofx/banco-a-1x-cp1252.ofx")).single()

        val candidatas = candidatasDoOfx(extrato, conta)

        assertEquals(3, candidatas.size)
        assertTrue(candidatas.all { it.dedupeKey.startsWith("ofx:") })
        assertEquals("ofx:2026081500123456", candidatas.first().dedupeKey)
    }

    @Test
    fun `OFX sem FITID cai no hash`() {
        val semId = """
            OFXHEADER:100
            <OFX><BANKMSGSRSV1><STMTRS><BANKACCTFROM><ACCTID>9<BANKTRANLIST>
            <STMTTRN><DTPOSTED>20260802<TRNAMT>-10,00<MEMO>PADARIA</STMTTRN>
            </BANKTRANLIST></STMTRS></BANKMSGSRSV1></OFX>
        """.trimIndent().toByteArray()

        val candidata = candidatasDoOfx(parseOfx(semId).single(), conta).single()

        assertTrue(candidata.dedupeKey, candidata.dedupeKey.startsWith("h:"))
        assertNotNull(candidata.date)
    }
}
