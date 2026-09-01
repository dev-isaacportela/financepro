package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.data.repo.PayeeRuleRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-ACT-001 · REQ-ACT-002 — o app aprende onde o usuário gasta.
 *
 * Sem ML e sem serviço externo: memória das escolhas dele (ingestao.md §4). O
 * acerto é ruim nas primeiras importações e muito bom depois, porque as despesas
 * de uma pessoa se repetem — e é isso que este teste prova, do lado do
 * aprendizado e do lado da sugestão.
 */
@Req("REQ-ACT-001", "REQ-ACT-002")
class PayeeRuleTest : DbTest() {

    private fun regras() = PayeeRuleRepository(db.payeeRuleDao())

    private fun txns() = TxnRepository(db.txnDao(), regras())

    private fun despesa(descricao: String, categoria: Long?, conta: Long) = Txn(
        accountId = conta,
        type = TxnType.EXPENSE,
        amountCents = -45_90,
        date = LocalDate.of(2026, 8, 15),
        categoryId = categoria,
        description = descricao,
    )

    private fun cenario(): Pair<Long, Long> = runBlocking {
        val conta = db.accountDao().upsert(CONTA)
        val categoria = db.categoryDao().upsert(CATEGORIA.copy(name = "Alimentação"))
        conta to categoria
    }

    // ---------- REQ-ACT-001: aprender ----------

    @Test
    fun `salvar com categoria ensina, e a importacao seguinte ja vem categorizada`() = runBlocking {
        val (conta, categoria) = cenario()
        val repo = txns()

        repeat(3) { repo.salvar(despesa("PADARIA CENTRAL", categoria, conta)) }

        assertEquals(categoria, regras().sugerir("PADARIA CENTRAL"))
    }

    @Test
    fun `o contador sobe a cada vez, sem duplicar a regra`() = runBlocking {
        val (conta, categoria) = cenario()
        val repo = txns()

        repeat(3) { repo.salvar(despesa("PADARIA CENTRAL", categoria, conta)) }

        val regra = db.payeeRuleDao().porChave("PADARIA CENTRAL")
        assertNotNull(regra)
        // Uma linha só: `normalizedKey` é único, e a terceira gravação seria um
        // erro de índice se o repositório inserisse em vez de reforçar.
        assertEquals(3, regra?.hitCount)
    }

    @Test
    fun `a ultima correcao manda`() = runBlocking {
        val (conta, alimentacao) = cenario()
        val lazer = db.categoryDao().upsert(CATEGORIA.copy(name = "Lazer"))
        val repo = txns()

        repeat(3) { repo.salvar(despesa("CINEMA GLORIA", alimentacao, conta)) }
        repo.salvar(despesa("CINEMA GLORIA", lazer, conta))

        // Quando o usuário corrige uma sugestão, o que ele disse foi "não é
        // isso, é aquilo". Exigir três repetições para a correção valer faria
        // ele desistir antes.
        assertEquals(lazer, regras().sugerir("CINEMA GLORIA"))
    }

    @Test
    fun `transferencia nao ensina nada`() = runBlocking {
        val (conta, _) = cenario()
        val destino = db.accountDao().upsert(CONTA.copy(name = "Poupança"))
        val repo = txns()

        repo.salvar(
            despesa("PARA A POUPANCA", null, conta)
                .copy(type = TxnType.TRANSFER, counterAccountId = destino),
        )

        assertNull(regras().sugerir("PARA A POUPANCA"))
    }

    @Test
    fun `descricao vazia nao vira regra`() = runBlocking {
        val (conta, categoria) = cenario()
        val repo = txns()

        // O lançamento de três toques não pede descrição (Art. 18), então isto
        // é o caso comum — e uma chave vazia casaria com toda linha sem
        // descrição de todo extrato futuro.
        repo.salvar(despesa("", categoria, conta))
        repo.salvar(despesa("   ", categoria, conta))

        assertNull(regras().sugerir(""))
        assertNull(db.payeeRuleDao().porChave(""))
    }

    @Test
    fun `so a primeira parcela ensina`() = runBlocking {
        val (conta, categoria) = cenario()
        val repo = txns()

        repo.salvarParcelado(
            (1..12).map { despesa("LOJA XYZ", categoria, conta).copy(installmentIndex = it) },
        )

        // Doze parcelas são uma compra só. Contá-las doze vezes inflaria o
        // contador sem ensinar nada de novo.
        assertEquals(1, db.payeeRuleDao().porChave("LOJA XYZ")?.hitCount)
    }

    // ---------- REQ-ACT-002 e REQ-ACT-004: sugerir ----------

    @Test
    fun `a sugestao usa a mesma normalizacao do dedupe`() = runBlocking {
        val (conta, categoria) = cenario()
        txns().salvar(despesa("PADARIA 00123456", categoria, conta))

        // NSU diferente, mesma padaria. É o que REQ-ACT-004 garante ao exigir
        // uma normalização só: sem ela, o dedupe trataria as duas como a mesma
        // transação e o aprendizado como duas lojas.
        assertEquals(categoria, regras().sugerir("Padaria 00987654"))
    }

    @Test
    fun `estabelecimento desconhecido nao tem sugestao`() = runBlocking {
        cenario()

        assertNull(regras().sugerir("LOJA QUE NUNCA VI"))
    }

    @Test
    fun `regra de palavra-chave casa com a descricao inteira do banco`() = runBlocking {
        val (_, categoria) = cenario()
        db.payeeRuleDao().aprender("IFOOD", categoria)

        // É o formato das ~40 regras semeadas (REQ-ACT-003): a chave é a
        // palavra, e o extrato traz "iFood *Pedido 12345". Casamento por
        // igualdade exata faria as quarenta nunca acertarem nada, e a primeira
        // importação chegaria vazia — o oposto do que a semente existe para
        // fazer. O conteúdo da semente é do `SeedTest`; aqui é o mecanismo.
        assertEquals(categoria, regras().sugerir("iFood *Pedido 12345"))
    }

    @Test
    fun `palavra-chave nao casa no meio de outra palavra`() = runBlocking {
        val (_, categoria) = cenario()
        db.payeeRuleDao().aprender("UBER", categoria)

        assertNull(regras().sugerir("SUBERBIA MODAS"))
    }

    @Test
    fun `entre duas regras que casam, a mais especifica ganha`() = runBlocking {
        val (_, transporte) = cenario()
        val viagem = db.categoryDao().upsert(CATEGORIA.copy(name = "Viagem"))
        db.payeeRuleDao().aprender("UBER", transporte)
        db.payeeRuleDao().aprender("UBER TRIP AEROPORTO", viagem)

        // A mais longa é a que o usuário ensinou, e descreve melhor o que ele fez.
        assertEquals(viagem, regras().sugerir("UBER TRIP AEROPORTO"))
    }
}
