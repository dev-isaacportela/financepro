package app.financepro.data.ingest

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-IMP-002 · REQ-IMP-003 — três arquivos, três jeitos de o parser errar.
 *
 * As fixtures são **sintéticas**, montadas sobre os três formatos que de fato
 * divergem no Brasil: 1.x SGML em CP1252, 2.x XML em UTF-8, e 1.x com vírgula
 * decimal e mais de uma conta no mesmo arquivo. Não são downloads reais de banco
 * — a T-041 vai passar arquivos de verdade antes de a importação ir para o
 * usuário, e é lá que o formato de algum banco vai surpreender.
 *
 * O que já está coberto é o que se conhece de antemão: acento em CP1252, tag sem
 * fechamento, `TRNTYPE` mentindo sobre o sinal, `FITID` repetido, e duas contas
 * no mesmo download.
 */
@Req("REQ-IMP-002", "REQ-IMP-003")
class OfxParserTest {

    private fun arquivo(nome: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/ofx/$nome")) { "fixture ausente: $nome" }
            .use { it.readBytes() }

    private fun extratos(nome: String) = parseOfx(arquivo(nome))

    // ---------- 1.x SGML, CP1252 ----------

    @Test
    fun `le o extrato 1x inteiro, com tag de folha sem fechamento`() {
        val extrato = extratos("banco-a-1x-cp1252.ofx").single()

        assertEquals("1234567-8", extrato.acctId)
        assertEquals(3, extrato.txns.size)
    }

    @Test
    fun `CHARSET 1252 devolve o acento certo`() {
        // O caso que REQ-IMP-003 soletra. Lido como UTF-8, o Ç vira o caractere
        // de substituição e o dedupe passa a tratar a mesma loja como duas.
        val descricoes = extratos("banco-a-1x-cp1252.ofx").single().txns.map { it.description }

        assertTrue(descricoes.toString(), "PADARIA ALIMENTAÇÃO" in descricoes)
    }

    @Test
    fun `NAME ganha de MEMO quando os dois existem`() {
        val comOsDois = extratos("banco-a-1x-cp1252.ofx").single().txns
            .single { it.fitid == "2026081600123457" }

        // O arquivo traz MEMO "COMPRA CARTAO", que descreve a operação, não a
        // loja. NAME é quem diz onde se gastou.
        assertEquals("PADARIA ALIMENTAÇÃO", comOsDois.description)
    }

    @Test
    fun `valor e data saem do arquivo, sem hora nem fuso`() {
        val compra = extratos("banco-a-1x-cp1252.ofx").single().txns.first()

        assertEquals(-187_50L, compra.amountCents)
        // `20260815120000[-3:BRT]` — a hora é do processamento no banco, e usá-la
        // faria uma compra da meia-noite mudar de dia conforme o fuso do arquivo.
        assertEquals(LocalDate.of(2026, 8, 15), compra.date)
        assertEquals("2026081500123456", compra.fitid)
    }

    @Test
    fun `credito entra positivo`() {
        val salario = extratos("banco-a-1x-cp1252.ofx").single().txns
            .single { it.description == "SALARIO" }

        assertEquals(5_000_00L, salario.amountCents)
    }

    // ---------- 2.x XML ----------

    @Test
    fun `o mesmo caminho de codigo le o XML 2x`() {
        val extrato = extratos("banco-b-2x-utf8.ofx").single()

        assertEquals("5555000011112222", extrato.acctId)
        assertEquals(2, extrato.txns.size)
    }

    @Test
    fun `entidade XML e decodificada`() {
        val padaria = extratos("banco-b-2x-utf8.ofx").single().txns.first()

        assertEquals("PADARIA & CIA", padaria.description)
    }

    @Test
    fun `o sinal de TRNAMT manda, e TRNTYPE e ignorado`() {
        // REQ-IMP-002 diz isso com todas as letras, e a fixture traz o caso:
        // TRNTYPE CREDIT com valor negativo. Há banco que faz.
        val estorno = extratos("banco-b-2x-utf8.ofx").single().txns.single { it.fitid == "NU-2" }

        assertEquals(-12_34L, estorno.amountCents)
    }

    // ---------- 1.x com duas contas, vírgula decimal e FITID repetido ----------

    @Test
    fun `arquivo multiconta vira um extrato por conta`() {
        val extratos = extratos("banco-c-1x-multiconta.ofx")

        assertEquals(listOf("111-1", "222-2"), extratos.map { it.acctId })
        // Quem importa escolhe a conta; o parser não decide por ninguém.
        assertEquals(2, extratos.first { it.acctId == "111-1" }.txns.size)
        assertEquals(1, extratos.first { it.acctId == "222-2" }.txns.size)
    }

    @Test
    fun `virgula decimal com ponto de milhar vira centavos`() {
        val aluguel = extratos("banco-c-1x-multiconta.ofx").first().txns.first()

        // `-1.234,56` — o exportador brasileiro que escreve assim é comum, e
        // `parseCents` já resolve os dois formatos (REQ-IMP-004).
        assertEquals(-123_456L, aluguel.amountCents)
    }

    @Test
    fun `FITID repetido no proprio arquivo nao quebra a leitura`() {
        val conta = extratos("banco-c-1x-multiconta.ofx").first()

        // As duas linhas chegam inteiras. Decidir o que fazer com o par é do
        // motor de dedupe (T-039) — o parser que descartasse uma aqui esconderia
        // do usuário uma transação que pode ser legítima.
        assertEquals(listOf("REP-1", "REP-1"), conta.txns.map { it.fitid })
        assertEquals(listOf(-123_456L, -20_00L), conta.txns.map { it.amountCents })
    }

    @Test
    fun `acento em ISO-8859-1 tambem sai certo`() {
        val farmacia = extratos("banco-c-1x-multiconta.ofx").first().txns[1]

        assertEquals("FARMÁCIA", farmacia.description)
    }

    // ---------- o que não é OFX ----------

    @Test
    fun `arquivo trocado por engano devolve lista vazia, nao excecao`() {
        // A tela de importação precisa dizer "não consegui ler este arquivo".
        // Uma exceção aqui seria um crash no meio de escolher um arquivo.
        assertEquals(emptyList<OfxStatement>(), parseOfx("uma foto, não um extrato".toByteArray()))
    }

    @Test
    fun `STMTTRN sem valor nao vira transacao, e o resto do extrato continua`() {
        val quebrado = """
            OFXHEADER:100
            <OFX><BANKMSGSRSV1><STMTRS><BANKACCTFROM><ACCTID>9<BANKTRANLIST>
            <STMTTRN><DTPOSTED>20260801<MEMO>SEM VALOR</STMTTRN>
            <STMTTRN><DTPOSTED>20260802<TRNAMT>-10,00<MEMO>BOA</STMTTRN>
            </BANKTRANLIST></STMTRS></BANKMSGSRSV1></OFX>
        """.trimIndent().toByteArray()

        val txns = parseOfx(quebrado).single().txns

        assertEquals(1, txns.size)
        assertEquals("BOA", txns.single().description)
        assertNull(txns.single().fitid)
    }
}
