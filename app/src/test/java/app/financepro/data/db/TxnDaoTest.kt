package app.financepro.data.db

import android.database.sqlite.SQLiteConstraintException
import app.financepro.core.testing.Req
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// A metade de REQ-TXN-012 que é do banco: a janela de datas e o filtro de
// conta. A combinação com categoria, tipo e busca é regra, e mora em
// `TxnListTest` — o `LIKE` do SQLite não dobra acento, e a conversão de
// texto para centavos não pode ter uma segunda implementação (Art. 6).
@Req("REQ-TXN-001", "REQ-DATA-002", "REQ-TXN-012", "REQ-IMP-012")
class TxnDaoTest : DbTest() {

    private val dao get() = db.txnDao()
    private val accounts get() = db.accountDao()
    private val categories get() = db.categoryDao()

    @Test
    fun `cria edita exclui e lista transacao`() = runBlocking {
        val conta = accounts.upsert(CONTA)
        val cat = categories.upsert(CATEGORIA.copy(name = "Mercado"))

        val id = dao.upsert(
            LANCAMENTO.copy(
                accountId = conta,
                categoryId = cat,
                amountCents = -18_50,
                description = "Padaria",
            ),
        )
        assertEquals("Padaria", dao.byId(id)?.description)

        dao.upsert(dao.byId(id)!!.copy(amountCents = -22_00))
        assertEquals(-22_00L, dao.byId(id)?.amountCents)

        dao.delete(dao.byId(id)!!)
        assertNull(dao.byId(id))
    }

    @Test
    fun `filtro de periodo compara epochDay`() = runBlocking {
        val conta = accounts.upsert(CONTA)
        val emConta = LANCAMENTO.copy(accountId = conta)
        dao.upsert(emConta.copy(date = dia(2026, 2, 28), description = "fora"))
        dao.upsert(emConta.copy(date = dia(2026, 3, 1), description = "borda"))
        dao.upsert(emConta.copy(date = dia(2026, 3, 31), description = "borda"))
        dao.upsert(emConta.copy(date = dia(2026, 4, 1), description = "fora"))

        val marco = dao.observeBetween(dia(2026, 3, 1), dia(2026, 3, 31)).first()

        // BETWEEN é inclusivo nas duas pontas: os dois dias de borda entram.
        assertEquals(listOf("borda", "borda"), marco.map { it.description })
    }

    @Test
    fun `extrato da conta inclui a perna de destino da transferencia`() = runBlocking {
        // ADR-003: a transferência é uma linha só, com accountId na origem.
        // Sem `counterAccountId` no filtro, ela não apareceria no extrato de
        // quem recebeu — e o usuário veria dinheiro entrar do nada.
        val origem = accounts.upsert(CONTA.copy(name = "Corrente"))
        val destino = accounts.upsert(CONTA.copy(name = "Poupança", type = AccountType.SAVINGS))
        dao.upsert(
            LANCAMENTO.copy(
                accountId = origem,
                counterAccountId = destino,
                type = TxnType.TRANSFER,
                amountCents = -100_000,
                description = "Reserva",
            ),
        )

        assertEquals(1, dao.observeByAccount(destino, dia(2026, 1, 1), dia(2026, 12, 31)).first().size)
        assertEquals(1, dao.observeByAccount(origem, dia(2026, 1, 1), dia(2026, 12, 31)).first().size)
    }

    @Test
    fun `conta e periodo se combinam com E`() = runBlocking {
        // Nada provava o `AND` de `observeByAccount`: uma linha na conta certa
        // mas fora da janela precisa ficar de fora, senão o extrato de março
        // traria fevereiro junto.
        val alvo = accounts.upsert(CONTA.copy(name = "Alvo"))
        val outra = accounts.upsert(CONTA.copy(name = "Outra"))
        dao.upsert(LANCAMENTO.copy(accountId = alvo, date = dia(2026, 3, 10), description = "dentro"))
        dao.upsert(LANCAMENTO.copy(accountId = alvo, date = dia(2026, 2, 10), description = "fora da janela"))
        dao.upsert(LANCAMENTO.copy(accountId = outra, date = dia(2026, 3, 10), description = "outra conta"))

        val marco = dao.observeByAccount(alvo, dia(2026, 3, 1), dia(2026, 3, 31)).first()

        assertEquals(listOf("dentro"), marco.map { it.description })
    }

    @Test
    fun `apagar a conta leva as transacoes junto`() = runBlocking {
        val conta = accounts.upsert(CONTA)
        val id = dao.upsert(LANCAMENTO.copy(accountId = conta))

        accounts.delete(accounts.byId(conta)!!)

        assertNull(dao.byId(id))
    }

    @Test
    fun `RESTRICT impede apagar categoria com transacao`() = runBlocking {
        // Prova que `PRAGMA foreign_keys = ON` está de fato ligado: sem ele o
        // SQLite aceita a DDL com RESTRICT e ignora a regra em silêncio, e este
        // DELETE passaria — deixando a transação apontando para o nada.
        val conta = accounts.upsert(CONTA)
        val cat = categories.upsert(CATEGORIA.copy(name = "Mercado"))
        dao.upsert(LANCAMENTO.copy(accountId = conta, categoryId = cat))

        val erro = runCatching { categories.delete(categories.byId(cat)!!) }.exceptionOrNull()

        assertTrue("esperava SQLiteConstraintException, veio $erro", erro is SQLiteConstraintException)
        assertEquals("Mercado", categories.byId(cat)?.name)
    }

    @Test
    fun `conta inexistente e recusada pela chave estrangeira`() = runBlocking {
        val erro = runCatching { dao.insert(LANCAMENTO.copy(accountId = 999)) }.exceptionOrNull()

        assertTrue("esperava SQLiteConstraintException, veio $erro", erro is SQLiteConstraintException)
    }

    @Test
    fun `dedupeKey repetido na mesma conta e recusado pelo indice unico`() = runBlocking {
        // Rede de segurança embaixo do dedupe da F2 (ingestao.md §3): se a
        // checagem em código falhar, o INSERT falha em vez de sujar os dados.
        val conta = accounts.upsert(CONTA)
        val comChave = LANCAMENTO.copy(accountId = conta, dedupeKey = "abc")
        dao.insert(comChave)

        val erro = runCatching { dao.insert(comChave) }.exceptionOrNull()

        assertTrue("esperava SQLiteConstraintException, veio $erro", erro is SQLiteConstraintException)
    }

    @Test
    fun `dedupeKey nulo nao colide, e a mesma chave em outra conta tambem nao`() = runBlocking {
        // É por isto que o índice pode ser total em vez de parcial: no SQLite
        // dois NULL nunca são iguais dentro de um índice único.
        val conta = accounts.upsert(CONTA.copy(name = "A"))
        val outra = accounts.upsert(CONTA.copy(name = "B"))

        dao.insert(LANCAMENTO.copy(accountId = conta))
        dao.insert(LANCAMENTO.copy(accountId = conta))
        dao.insert(LANCAMENTO.copy(accountId = conta, dedupeKey = "abc"))
        dao.insert(LANCAMENTO.copy(accountId = outra, dedupeKey = "abc"))

        assertEquals(4, dao.observeBetween(dia(2026, 1, 1), dia(2026, 12, 31)).first().size)
    }
}
