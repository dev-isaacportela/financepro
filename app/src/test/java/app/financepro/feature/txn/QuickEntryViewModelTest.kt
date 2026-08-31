package app.financepro.feature.txn

import android.os.Looper
import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIAS_PADRAO
import app.financepro.data.db.CONTA
import app.financepro.data.db.DbTest
import app.financepro.data.db.SeedCallback
import app.financepro.data.db.dia
import app.financepro.data.db.toDomain
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.balanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

/**
 * O lançamento rápido de ponta a ponta. REQ-UI-002 · REQ-TXN-002 · REQ-CAT-006
 *
 * ViewModel construído à mão sobre o banco em memória, sem Hilt: os repositórios
 * não têm interface (Art. 10), então injetá-los é passar os argumentos. Um mock
 * provaria menos e custaria uma biblioteca.
 *
 * **Nada de `runBlocking` esperando o estado.** `viewModelScope` roda em
 * `Dispatchers.Main`, que no Robolectric é o looper principal — o mesmo que o
 * teste ocupa. Suspender ali esperando uma emissão trava o teste para sempre,
 * porque quem produziria a emissão é a thread que está bloqueada.
 *
 * [esperar] roda o looper em vez de esperar nele, e repete até a condição valer:
 * o `Flow` do Room emite de um executor próprio, então rodar o looper uma vez só
 * ganha uma corrida na maioria das execuções e perde nas outras.
 */
@Req("REQ-UI-002", "REQ-CAT-006")
class QuickEntryViewModelTest : DbTest() {

    private lateinit var vm: QuickEntryViewModel
    private var carteira = 0L

    private val hoje = LocalDate.of(2026, 3, 10)
    private val alimentacao get() = CATEGORIAS_PADRAO.first { it.nome == "Alimentação" }.id

    @Before
    fun montar() {
        SeedCallback.onCreate(db.openHelper.writableDatabase)
        carteira = runBlocking {
            db.accountDao().upsert(
                CONTA.copy(name = "Carteira", type = AccountType.CASH, initialBalanceCents = 350_00),
            )
        }
        vm = QuickEntryViewModel(
            AccountRepository(db.accountDao()),
            CategoryRepository(db.categoryDao()),
            TxnRepository(db.txnDao()),
        )
        esperar("contas e categorias chegarem ao estado") {
            vm.state.value.contas.isNotEmpty() && vm.state.value.categorias.isNotEmpty()
        }
    }

    @Test
    fun `despesa vira valor negativo e derruba o saldo`() {
        vm.valor(18_50)
        vm.categoria(alimentacao)

        vm.salvar(hoje)
        esperar("a gravação terminar") { vm.state.value.salvo }

        // REQ-TXN-002: quem digita 18,50 numa despesa grava −1850. O sinal é
        // convenção do banco, e pedi-lo ao usuário seria mudá-la de lugar.
        assertEquals(listOf(-18_50L), gravadas().map { it.amountCents })
        val conta = vm.state.value.contas.first { it.id == carteira }
        assertEquals(331_50L, balanceOf(conta, gravadas().map { it.toDomain() }))
    }

    @Test
    fun `salvar sem categoria nao grava e devolve a mensagem da spec`() {
        vm.valor(18_50)

        vm.salvar(hoje)

        // Validação é síncrona: não há o que esperar quando nada é gravado.
        assertEquals(listOf("Escolha uma categoria"), vm.state.value.erros.map { it.mensagem })
        assertTrue(gravadas().isEmpty())
    }

    @Test
    fun `a folha abre limpa na segunda vez`() {
        // O bug que só apareceu no aparelho: a folha vive fora do NavHost, então
        // o ViewModel é o da Activity e sobrevive ao fechamento. Com `salvo`
        // ligado, a reabertura se fechava sozinha na primeira composição — e o
        // sintoma era o botão de lançar simplesmente parar de responder.
        vm.valor(18_50)
        vm.categoria(alimentacao)
        vm.salvar(hoje)
        esperar("a gravação terminar") { vm.state.value.salvo }

        vm.concluido()

        assertFalse(vm.state.value.salvo)
        assertEquals(0L, vm.state.value.cents)
        assertNull(vm.state.value.categoriaId)
        // O que veio do banco fica: recarregar as contas a cada abertura piscaria
        // os chips na cara de quem só quer lançar de novo.
        assertTrue(vm.state.value.contas.isNotEmpty())
    }

    @Test
    fun `salvar empurra a categoria para o topo do grid`() {
        val lazer = CATEGORIAS_PADRAO.first { it.nome == "Lazer" }.id
        vm.valor(30_00)
        vm.categoria(lazer)

        vm.salvar(hoje)
        esperar("a gravação terminar") { vm.state.value.salvo }

        // REQ-CAT-006: o grid ordena por useCount decrescente, então a categoria
        // recém-usada passa a ser a primeira oferecida.
        val grid = runBlocking { db.categoryDao().observeByUse(CategoryKind.EXPENSE.name).first() }
        assertEquals("Lazer", grid.first().name)
    }

    @Test
    fun `transferencia nao leva categoria`() {
        val destino = runBlocking { db.accountDao().upsert(CONTA.copy(name = "Corrente")) }
        esperar("a conta de destino chegar ao estado") { vm.state.value.contas.size > 1 }
        vm.tipo(TxnType.TRANSFER)
        vm.valor(100_00)
        vm.destino(destino)

        vm.salvar(hoje)
        esperar("a gravação terminar") { vm.state.value.salvo }

        val gravada = gravadas().single()
        assertNull(gravada.categoryId)
        assertEquals(destino, gravada.counterAccountId)
        assertEquals(-100_00L, gravada.amountCents)
    }

    private fun gravadas() =
        runBlocking { db.txnDao().observeBetween(dia(2026, 1, 1), dia(2026, 12, 31)).first() }

    /**
     * Roda o looper principal até [condicao] valer, sem bloquear nele.
     *
     * O limite existe para o teste falhar dizendo o que faltou, em vez de
     * pendurar a suíte inteira — que foi como esta classe se comportou na
     * primeira versão.
     */
    private fun esperar(descricao: String, condicao: () -> Boolean) {
        val limite = System.nanoTime() + LIMITE_NANOS
        while (System.nanoTime() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicao()) return
            Thread.sleep(PASSO_MS)
        }
        error("tempo esgotado esperando: $descricao")
    }

    private companion object {
        const val LIMITE_NANOS = 5_000_000_000L
        const val PASSO_MS = 5L
    }
}
