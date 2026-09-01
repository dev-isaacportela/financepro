package app.financepro.data.ingest

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-IMP-007 · REQ-IMP-008 · REQ-IMP-009
 *
 * Errar aqui tem duas caras, e as duas são graves: importar duas vezes duplica a
 * vida financeira do usuário, e descartar demais some com transação que ele fez.
 * Por isso o motor só decide sozinho quando a chave é exata — o resto vira
 * pergunta.
 */
@Req("REQ-IMP-007", "REQ-IMP-008", "REQ-IMP-009")
class DedupeTest {

    private val conta = 1L
    private val dia = LocalDate.of(2026, 8, 15)

    private fun candidata(
        descricao: String = "PADARIA",
        cents: Long = -45_90,
        data: LocalDate = dia,
        chave: String? = null,
    ) = Candidata(
        date = data,
        amountCents = cents,
        description = descricao,
        dedupeKey = chave ?: dedupeKey(conta, data, cents, descricao),
    )

    private fun gravada(
        id: Long = 1,
        descricao: String = "PADARIA",
        cents: Long = -45_90,
        data: LocalDate = dia,
        chave: String? = null,
    ) = JaGravada(
        id = id,
        date = data,
        amountCents = cents,
        description = descricao,
        dedupeKey = chave ?: dedupeKey(conta, data, cents, descricao),
    )

    // ---------- REQ-IMP-007: FITID ----------

    @Test
    fun `FITID ja gravado e descartado sem perguntar`() {
        val jaTem = gravada(chave = chaveOfx("2026081500123456"))
        val vindo = candidata(descricao = "OUTRO TEXTO", chave = chaveOfx("2026081500123456"))

        val avaliada = avaliar(listOf(vindo), listOf(jaTem)).single()

        // A descrição mudou e o valor é o mesmo: o banco reescreveu o texto, e o
        // FITID é o que diz que é a mesma linha.
        assertEquals(Veredito.DUPLICATA, avaliada.veredito)
        assertEquals(jaTem, avaliada.parecida)
    }

    @Test
    fun `FITID novo passa`() {
        val avaliada = avaliar(
            listOf(candidata(chave = chaveOfx("NOVO"))),
            listOf(gravada(chave = chaveOfx("VELHO"), data = dia.minusDays(30))),
        ).single()

        assertEquals(Veredito.NOVA, avaliada.veredito)
    }

    @Test
    fun `chave de OFX nao colide com chave de hash`() {
        // Os prefixos existem para isso: um FITID que por acaso parecesse um
        // hash descartaria uma linha de CSV legítima.
        val porHash = gravada(data = dia.minusDays(30))

        val avaliada = avaliar(listOf(candidata(chave = chaveOfx("2026081500123456"))), listOf(porHash))

        assertEquals(Veredito.NOVA, avaliada.single().veredito)
    }

    // ---------- REQ-IMP-008: hash ----------

    @Test
    fun `reimportar o mesmo arquivo descarta tudo`() {
        val extrato = listOf(
            candidata("SUPERMERCADO XYZ", -187_50),
            candidata("PADARIA 00123456", -45_90),
            candidata("SALARIO", 5_000_00),
        )
        val jaGravadas = extrato.mapIndexed { i, c ->
            JaGravada(i + 1L, c.date, c.amountCents, c.description, c.dedupeKey)
        }

        val avaliadas = avaliar(extrato, jaGravadas)

        assertEquals(List(3) { Veredito.DUPLICATA }, avaliadas.map { it.veredito })
    }

    @Test
    fun `NSU diferente na mesma compra ainda e a mesma chave`() {
        // A normalização da T-036 tira a sequência longa; o hash vem igual, e a
        // segunda importação do mesmo dia não duplica a padaria.
        val jaTem = gravada(descricao = "PADARIA 00123456")
        val vindo = candidata(descricao = "PADARIA 00987654")

        assertEquals(Veredito.DUPLICATA, avaliar(listOf(vindo), listOf(jaTem)).single().veredito)
    }

    @Test
    fun `a mesma chave repetida dentro do proprio arquivo cai na segunda vez`() {
        // O arquivo com FITID repetido da T-037. Sem isto a segunda cópia
        // passaria como novidade e só esbarraria no índice único, na gravação.
        val repetida = candidata(chave = chaveOfx("REP-1"))

        val avaliadas = avaliar(listOf(repetida, repetida), emptyList())

        assertEquals(listOf(Veredito.NOVA, Veredito.DUPLICATA), avaliadas.map { it.veredito })
        // A segunda não aponta para nada gravado, porque não havia nada gravado.
        assertNull(avaliadas[1].parecida)
    }

    // ---------- REQ-IMP-009: janela de ±3 dias ----------

    @Test
    fun `valor igual dentro de tres dias vira possivel duplicata, e nao some`() {
        val jaTem = gravada(descricao = "MERCADO CENTRAL", data = dia.minusDays(2))
        val vindo = candidata(descricao = "MERCADO CENTRAL LTDA")

        val avaliada = avaliar(listOf(vindo), listOf(jaTem)).single()

        assertEquals(Veredito.POSSIVEL_DUPLICATA, avaliada.veredito)
        // A outra linha vai junto: sem ela à vista, o aviso é injulgável.
        assertEquals(jaTem, avaliada.parecida)
    }

    @Test
    fun `a borda de tres dias entra, e a de quatro nao`() {
        val jaTem = gravada(descricao = "MERCADO", data = dia)

        val naBorda = avaliar(listOf(candidata("OUTRO", data = dia.plusDays(3))), listOf(jaTem))
        val fora = avaliar(listOf(candidata("OUTRO", data = dia.plusDays(4))), listOf(jaTem))

        assertEquals(Veredito.POSSIVEL_DUPLICATA, naBorda.single().veredito)
        assertEquals(Veredito.NOVA, fora.single().veredito)
    }

    @Test
    fun `a janela vale para tras e para frente`() {
        val jaTem = gravada(descricao = "MERCADO", data = dia)

        val antes = avaliar(listOf(candidata("OUTRO", data = dia.minusDays(3))), listOf(jaTem))

        assertEquals(Veredito.POSSIVEL_DUPLICATA, antes.single().veredito)
    }

    @Test
    fun `valor diferente na mesma data e transacao nova`() {
        val jaTem = gravada(descricao = "MERCADO", cents = -50_00)

        val avaliada = avaliar(listOf(candidata("MERCADO", cents = -50_01)), listOf(jaTem))

        // Um centavo de diferença é outra compra. A janela é de data, não de valor.
        assertEquals(Veredito.NOVA, avaliada.single().veredito)
    }

    @Test
    fun `descricao igual com data deslocada tambem e marcada`() {
        // O caso que a spec não escreve e que é a duplicata mais óbvia de todas:
        // o extrato lançou a mesma compra um dia depois. A data entra no hash,
        // então o nível 1 não pega.
        val jaTem = gravada(descricao = "ALUGUEL", cents = -1_500_00, data = dia)
        val vindo = candidata("ALUGUEL", cents = -1_500_00, data = dia.plusDays(1))

        assertEquals(Veredito.POSSIVEL_DUPLICATA, avaliar(listOf(vindo), listOf(jaTem)).single().veredito)
    }

    @Test
    fun `entre duas parecidas, aponta a mais proxima em data`() {
        val longe = gravada(id = 1, descricao = "MERCADO", data = dia.minusDays(3))
        val perto = gravada(id = 2, descricao = "MERCADO", data = dia.minusDays(1))

        val avaliada = avaliar(listOf(candidata("OUTRO")), listOf(longe, perto)).single()

        assertEquals(perto, avaliada.parecida)
    }

    // ---------- a ordem, que a tela de revisão depende ----------

    @Test
    fun `a ordem do arquivo e preservada`() {
        val extrato = listOf(candidata("A", -1_00), candidata("B", -2_00), candidata("C", -3_00))

        val avaliadas = avaliar(extrato, emptyList())

        assertEquals(listOf("A", "B", "C"), avaliadas.map { it.candidata.description })
    }

    @Test
    fun `conta vazia aceita tudo`() {
        val avaliadas = avaliar(listOf(candidata("A"), candidata("B", -2_00)), emptyList())

        assertEquals(List(2) { Veredito.NOVA }, avaliadas.map { it.veredito })
    }
}
