package app.financepro.feature.categories

import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIA
import app.financepro.data.db.CONTA
import app.financepro.data.db.DbTest
import app.financepro.data.db.LANCAMENTO
import app.financepro.data.db.dia
import app.financepro.data.repo.CategoryRepository
import app.financepro.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * REQ-CAT-005 — exclusão protegida, com recategorização em lote.
 *
 * A proteção é do banco (`ON DELETE RESTRICT`), e `TxnDaoTest` já prova que ela
 * dispara. O que se prova aqui é o que a UI faz com ela: contar antes de
 * oferecer, mover o lote numa escrita só, e não deixar transação sem categoria
 * pelo caminho — que é o que REQ-TXN-005 proíbe.
 */
@Req("REQ-CAT-005")
class CategoriesViewModelTest : DbTest() {

    private lateinit var vm: CategoriesViewModel
    private var mercado = 0L
    private var lazer = 0L
    private var conta = 0L

    @Before
    fun montar() {
        runBlocking {
            conta = db.accountDao().upsert(CONTA)
            mercado = db.categoryDao().upsert(CATEGORIA.copy(name = "Mercado"))
            lazer = db.categoryDao().upsert(CATEGORIA.copy(name = "Lazer"))
        }
        vm = CategoriesViewModel(CategoryRepository(db.categoryDao(), db.txnDao()))
        esperar("as categorias chegarem") { vm.state.value.categorias.size == 2 }
    }

    @Test
    fun `categoria sem transacao some direto`() {
        vm.pedirExclusao(vm.state.value.categorias.first { it.id == lazer })
        esperar("a contagem chegar") { vm.state.value.excluindo != null }
        assertEquals(0, vm.state.value.excluindo?.presas)

        vm.confirmarExclusao()
        esperar("a exclusão terminar") { vm.state.value.excluindo == null }

        assertNull(runBlocking { db.categoryDao().byId(lazer) })
    }

    @Test
    fun `com transacoes, a mensagem diz quantas e a exclusao espera destino`() {
        runBlocking {
            repeat(3) { db.txnDao().insert(LANCAMENTO.copy(accountId = conta, categoryId = mercado)) }
        }

        vm.pedirExclusao(vm.state.value.categorias.first { it.id == mercado })
        esperar("a contagem chegar") { vm.state.value.excluindo != null }

        // A mensagem exata da spec, com o N que a torna acionável.
        assertEquals("Mova as 3 transações antes", vm.state.value.excluindo?.mensagem)

        vm.confirmarExclusao()

        // Sem destino não excluiu, e nada foi perdido pelo caminho.
        assertTrue(runBlocking { db.categoryDao().byId(mercado) } != null)
        assertEquals(3, runBlocking { db.categoryDao().contarTransacoes(mercado) })
    }

    @Test
    fun `mover e excluir leva o lote inteiro para a categoria escolhida`() {
        runBlocking {
            repeat(3) { db.txnDao().insert(LANCAMENTO.copy(accountId = conta, categoryId = mercado)) }
        }
        vm.pedirExclusao(vm.state.value.categorias.first { it.id == mercado })
        esperar("a contagem chegar") { vm.state.value.excluindo != null }

        vm.destinoDaExclusao(lazer)
        vm.confirmarExclusao()
        esperar("a exclusão terminar") { vm.state.value.excluindo == null }

        assertNull(runBlocking { db.categoryDao().byId(mercado) })
        assertEquals(3, runBlocking { db.categoryDao().contarTransacoes(lazer) })
        // Nenhuma transação ficou sem categoria: é o que REQ-TXN-005 proíbe, e
        // seria o resultado de mover uma a uma e morrer no meio.
        val todas = runBlocking { db.txnDao().observeBetween(dia(2026, 1, 1), dia(2026, 12, 31)).first() }
        assertEquals(emptyList<Long?>(), todas.map { it.categoryId }.filter { it == null })
    }

    @Test
    fun `destino oferecido nunca mistura receita com despesa`() {
        runBlocking { db.categoryDao().upsert(CATEGORIA.copy(name = "Salário", kind = CategoryKind.INCOME)) }
        esperar("a terceira categoria chegar") { vm.state.value.categorias.size == 3 }

        val alvo = vm.state.value.categorias.first { it.id == mercado }

        // REQ-CAT-003: mover despesa para uma categoria de receita produziria
        // transação inválida assim que alguém a editasse.
        assertEquals(listOf("Lazer"), vm.state.value.destinosPara(alvo).map { it.name })
    }
}
