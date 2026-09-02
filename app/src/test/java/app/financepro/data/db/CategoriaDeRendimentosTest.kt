package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.data.repo.CategoryRepository
import app.financepro.domain.model.CategoryKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * REQ-INV-003 — a categoria que recebe o rendimento.
 *
 * O teste existe por causa da armadilha que ele descarta: a versão óbvia deste
 * módulo semeava "Rendimentos" no id 11 e a migração fazia `INSERT OR IGNORE`
 * no mesmo id. Só que os ids 1 a 10 são do seed, então **o 11 é o da primeira
 * categoria que o usuário criou** — o insert seria ignorado em silêncio em toda
 * instalação antiga, e o rendimento cairia na categoria que a pessoa chamou de
 * outra coisa. Nada no build pegaria isso.
 */
@Req("REQ-INV-003")
class CategoriaDeRendimentosTest : DbTest() {

    private val repo get() = CategoryRepository(db.categoryDao(), db.txnDao())

    @Test
    fun `cria a categoria na primeira vez`() = runBlocking {
        val id = repo.idDeRendimentos()

        val criada = db.categoryDao().byId(id)!!
        assertEquals(NOME_RENDIMENTOS, criada.name)
        assertEquals(CategoryKind.INCOME, criada.kind)
    }

    @Test
    fun `a segunda chamada reusa a primeira`() = runBlocking {
        assertEquals(repo.idDeRendimentos(), repo.idDeRendimentos())
    }

    @Test
    fun `nao rouba a categoria que o usuario criou no id 11`() = runBlocking {
        // O caso que derrubou o id fixo. Dez do seed, e a décima primeira é dela.
        val dela = db.categoryDao().upsert(
            CATEGORIA.copy(id = 11, name = "Pets", kind = CategoryKind.EXPENSE),
        )

        val rendimentos = repo.idDeRendimentos()

        assertNotEquals(dela, rendimentos)
        assertEquals("Pets", db.categoryDao().byId(dela)?.name)
        assertEquals(NOME_RENDIMENTOS, db.categoryDao().byId(rendimentos)?.name)
    }

    @Test
    fun `categoria de despesa com o mesmo nome nao serve`() = runBlocking {
        // Nome igual e tipo errado reprovaria em `validateTxn` (REQ-TXN-005),
        // e o lançamento morreria numa mensagem que ninguém liga à causa.
        db.categoryDao().upsert(CATEGORIA.copy(name = NOME_RENDIMENTOS, kind = CategoryKind.EXPENSE))

        val id = repo.idDeRendimentos()

        assertEquals(CategoryKind.INCOME, db.categoryDao().byId(id)?.kind)
    }

    @Test
    fun `arquivada nao e reusada`() = runBlocking {
        val primeira = repo.idDeRendimentos()
        db.categoryDao().upsert(db.categoryDao().byId(primeira)!!.copy(archived = true))

        val segunda = repo.idDeRendimentos()

        assertNotEquals(primeira, segunda)
    }
}
