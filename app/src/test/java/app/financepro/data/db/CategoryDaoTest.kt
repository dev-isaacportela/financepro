package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Req("REQ-CAT-001", "REQ-CAT-002")
class CategoryDaoTest : DbTest() {

    private val dao get() = db.categoryDao()

    @Test
    fun `cria edita arquiva e lista categoria`() = runBlocking {
        val id = dao.upsert(CATEGORIA.copy(name = "Mercado"))

        dao.upsert(dao.byId(id)!!.copy(name = "Supermercado"))
        assertEquals(listOf("Supermercado"), dao.observeActive().first().map { it.name })

        dao.upsert(dao.byId(id)!!.copy(archived = true))
        assertTrue(dao.observeActive().first().isEmpty())
        assertEquals("Supermercado", dao.byId(id)?.name)
    }

    @Test
    fun `subcategoria de categoria raiz e aceita`() = runBlocking {
        val mae = dao.upsertChecked(CATEGORIA.copy(name = "Alimentação"))
        val filha = dao.upsertChecked(CATEGORIA.copy(name = "Restaurante", parentId = mae))

        assertEquals(mae, dao.byId(filha)?.parentId)
        assertEquals(listOf("Restaurante"), dao.childrenOf(mae).map { it.name })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subcategoria de subcategoria e recusada`(): Unit = runBlocking {
        val mae = dao.upsertChecked(CATEGORIA.copy(name = "Alimentação"))
        val filha = dao.upsertChecked(CATEGORIA.copy(name = "Restaurante", parentId = mae))

        dao.upsertChecked(CATEGORIA.copy(name = "Japonês", parentId = filha))
    }

    @Test
    fun `apagar a mae leva as filhas junto`() = runBlocking {
        // CASCADE: subcategoria órfã apontaria para um parentId inexistente e
        // sumiria de qualquer listagem hierárquica, sem nunca ser apagada.
        val mae = dao.upsertChecked(CATEGORIA.copy(name = "Alimentação"))
        val filha = dao.upsertChecked(CATEGORIA.copy(name = "Restaurante", parentId = mae))

        dao.delete(dao.byId(mae)!!)

        assertNull(dao.byId(filha))
    }

    @Test
    fun `grid do lancamento rapido vem ordenado por uso`() = runBlocking {
        dao.upsert(CATEGORIA.copy(name = "Aluguel", useCount = 1))
        dao.upsert(CATEGORIA.copy(name = "Mercado", useCount = 9))
        dao.upsert(CATEGORIA.copy(name = "Salário", kind = CategoryKind.INCOME, useCount = 99))
        val transporte = dao.upsert(CATEGORIA.copy(name = "Transporte", useCount = 4))

        dao.bumpUse(transporte)
        dao.bumpUse(transporte)

        // Só EXPENSE: o grid de despesa não oferece "Salário", por mais usada
        // que ela seja.
        assertEquals(
            listOf("Mercado", "Transporte", "Aluguel"),
            dao.observeByUse(CategoryKind.EXPENSE.name).first().map { it.name },
        )
    }
}
