package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.data.repo.BudgetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * REQ-BUD-001 — no máximo um teto por par (categoria, mês).
 *
 * O índice único garante isso no banco; o teste prova que a **escrita** também
 * garante, porque um `@Upsert` ingênuo quebraria: em violação do índice ele cai
 * para um `UPDATE` pela chave primária, que numa linha nova (`id = 0`) não casa
 * com nada — a mesma armadilha documentada em `TxnDao.insert`.
 */
@Req("REQ-BUD-001")
class BudgetDaoTest : DbTest() {

    private val agosto = YearMonth.of(2026, 8)
    private val setembro = YearMonth.of(2026, 9)

    private fun repo() = BudgetRepository(db.budgetDao())

    private fun tetos() = runBlocking { db.budgetDao().observeAll().first() }

    @Test
    fun `definir duas vezes no mesmo par atualiza, e nao duplica`() = runBlocking {
        val repo = repo()
        val categoria = db.categoryDao().upsert(CATEGORIA)

        repo.definir(categoria, agosto, 600_00)
        repo.definir(categoria, agosto, 750_00)

        assertEquals(1, tetos().size)
        assertEquals(750_00L, tetos().single().limitCents)
    }

    @Test
    fun `o mesmo par em meses diferentes convive`() = runBlocking {
        val repo = repo()
        val categoria = db.categoryDao().upsert(CATEGORIA)

        repo.definir(categoria, agosto, 600_00)
        repo.definir(categoria, setembro, 400_00)

        assertEquals(listOf(202608, 202609), tetos().map { it.yearMonth }.sorted())
    }

    @Test
    fun `o yyyyMM atravessa a fronteira YearMonth sem perder o mes`() = runBlocking {
        val repo = repo()
        val categoria = db.categoryDao().upsert(CATEGORIA)

        // Dezembro é a linha que pega o erro de `year * 100 + month`: com um
        // deslocamento errado, 202612 voltaria como janeiro do ano seguinte.
        repo.definir(categoria, YearMonth.of(2026, 12), 100_00)

        assertEquals(202612, tetos().single().yearMonth)
        assertEquals(YearMonth.of(2026, 12), repo.observeAll().first().single().month)
    }

    @Test
    fun `remover apaga o teto daquele mes e deixa o do outro`() = runBlocking {
        val repo = repo()
        val categoria = db.categoryDao().upsert(CATEGORIA)
        repo.definir(categoria, agosto, 600_00)
        repo.definir(categoria, setembro, 400_00)

        repo.remover(categoria, agosto)

        assertEquals(listOf(202609), tetos().map { it.yearMonth })
    }

    @Test
    fun `teto zero ou negativo e recusado na escrita`() = runBlocking {
        val repo = repo()
        val categoria = db.categoryDao().upsert(CATEGORIA)

        // "Não gaste nada nesta categoria" não é um teto, é a ausência dele — e
        // é `remover` que diz isso. A regra também mantém `percent` livre de
        // divisão por zero.
        listOf(0L, -100L).forEach { valor ->
            val erro = runCatching { repo.definir(categoria, agosto, valor) }.exceptionOrNull()
            assertTrue("teto $valor deveria ser recusado", erro is IllegalArgumentException)
        }
        assertTrue(tetos().isEmpty())
    }

    @Test
    fun `excluir a categoria leva o teto junto`() = runBlocking {
        val repo = repo()
        val categoria = db.categoryDao().upsert(CATEGORIA)
        repo.definir(categoria, agosto, 600_00)

        db.categoryDao().delete(db.categoryDao().byId(categoria)!!)

        // `CASCADE` na FK: um teto órfão apontaria para uma categoria que não
        // existe, e a tela de orçamento mostraria uma linha sem nome.
        assertTrue(tetos().isEmpty())
        assertNull(db.budgetDao().byCategoryAndMonth(categoria, 202608))
    }
}
