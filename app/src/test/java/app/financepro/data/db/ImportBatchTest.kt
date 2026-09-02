package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.data.repo.PayeeRuleRepository
import app.financepro.data.repo.TxnRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-IMP-011 — o lote, e a válvula de escape.
 *
 * Sem o desfazer, uma importação errada de 400 linhas só se resolve apagando o
 * app (ingestao.md §3.1). Com ele, a pergunta passa a ser **o que** desfazer: a
 * linha que o usuário corrigiu depois é trabalho dele, e apagá-la para desfazer
 * o trabalho do app seria a troca errada.
 */
@Req("REQ-IMP-011")
class ImportBatchTest : DbTest() {

    private fun dao() = db.importBatchDao()

    private fun txns() = TxnRepository(db.txnDao(), PayeeRuleRepository(db.payeeRuleDao()))

    private fun cenario(quantas: Int = 3): Pair<Long, Long> = runBlocking {
        val conta = db.accountDao().upsert(CONTA)
        val categoria = db.categoryDao().upsert(CATEGORIA)
        val agora = 1_700_000_000_000L
        val loteId = dao().gravar(
            ImportBatchEntity(
                accountId = conta,
                sourceType = "OFX",
                sourceName = "extrato.ofx",
                importedAt = agora,
                txnCount = quantas,
            ),
            (1..quantas).map {
                LANCAMENTO.copy(
                    accountId = conta,
                    categoryId = categoria,
                    description = "LINHA $it",
                    dedupeKey = "ofx:$it",
                    // A importação grava os dois iguais. É essa igualdade que o
                    // desfazer usa para saber quem ninguém tocou.
                    createdAt = agora,
                    updatedAt = agora,
                )
            },
        )
        conta to loteId
    }

    @Test
    fun `gravar liga as linhas ao lote numa transacao so`() = runBlocking {
        val (_, loteId) = cenario()

        assertEquals(3, dao().contar(loteId))
        assertTrue(db.txnDao().todas().all { it.importBatchId == loteId })
    }

    @Test
    fun `desfazer remove exatamente as transacoes do lote`() = runBlocking {
        val (conta, loteId) = cenario()
        // Uma transação de fora do lote, que não pode ser tocada.
        db.txnDao().insert(LANCAMENTO.copy(accountId = conta, description = "MINHA"))

        val saldo = dao().desfazer(loteId)

        assertEquals(3, saldo.removidas)
        assertEquals(0, saldo.mantidas)
        assertEquals(listOf("MINHA"), db.txnDao().todas().map { it.description })
    }

    @Test
    fun `transacao editada depois da importacao nao e removida`() = runBlocking {
        val (_, loteId) = cenario()
        val alvo = db.txnDao().todas().first { it.description == "LINHA 2" }

        // O caminho real da edição: `TxnRepository.salvar` preserva o
        // `createdAt` e avança o `updatedAt`.
        txns().salvar(alvo.toDomain().copy(description = "CORRIGIDA À MÃO"))
        val saldo = dao().desfazer(loteId)

        assertEquals(2, saldo.removidas)
        assertEquals(1, saldo.mantidas)
        assertEquals(listOf("CORRIGIDA À MÃO"), db.txnDao().todas().map { it.description })
    }

    @Test
    fun `a linha que sobrou perde o vinculo com o lote, e vira transacao comum`() = runBlocking {
        val (_, loteId) = cenario()
        val alvo = db.txnDao().todas().first { it.description == "LINHA 1" }
        txns().salvar(alvo.toDomain().copy(description = "MINHA AGORA"))

        dao().desfazer(loteId)

        // `importBatchId` é SET_NULL: apagar o lote não pode levar junto o que o
        // usuário decidiu manter.
        assertNull(db.txnDao().todas().single().importBatchId)
    }

    @Test
    fun `o lote some da lista depois de desfeito`() = runBlocking {
        val (_, loteId) = cenario()

        dao().desfazer(loteId)

        assertEquals(0, dao().contar(loteId))
        assertTrue(dao().observeAll().first().isEmpty())
    }

    @Test
    fun `desfazer um lote nao mexe no outro`() = runBlocking {
        val (_, primeiro) = cenario()
        val (_, segundo) = cenario(quantas = 2)

        dao().desfazer(primeiro)

        assertEquals(2, dao().contar(segundo))
    }
}
