package app.financepro.feature.txn

import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIAS_PADRAO
import app.financepro.data.db.CONTA
import app.financepro.data.db.DbTest
import app.financepro.data.db.LANCAMENTO
import app.financepro.data.db.SeedCallback
import app.financepro.data.db.dia
import app.financepro.data.db.toDomain
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.EscopoDeParcela
import app.financepro.domain.usecase.balanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
@Req("REQ-UI-002", "REQ-CAT-006", "REQ-TXN-001", "REQ-TXN-003")
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
            CategoryRepository(db.categoryDao(), db.txnDao()),
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

    /**
     * REQ-TXN-001 — o toque na linha abre a folha preenchida, e salvar atualiza.
     *
     * A asserção que importa é `single()`: um `insert` no lugar do `update`
     * deixaria as duas linhas na tela, cada uma com um valor, e ninguém saberia
     * qual é a verdadeira.
     */
    @Test
    fun `editar carrega a folha e salvar atualiza em vez de criar outra`() {
        vm.valor(18_50)
        vm.categoria(alimentacao)
        vm.salvar(hoje)
        esperar("a gravação terminar") { vm.state.value.salvo }
        vm.concluido()
        val id = gravadas().single().id

        vm.editar(id)
        esperar("a transação carregar na folha") { vm.state.value.editando }

        // O campo mostra o valor **absoluto**: o sinal é convenção do banco
        // (REQ-TXN-002), e a folha nunca o mostrou.
        assertEquals(18_50L, vm.state.value.cents)
        assertEquals(alimentacao, vm.state.value.categoriaId)
        assertEquals(carteira, vm.state.value.contaId)

        vm.valor(22_00)
        vm.salvar(hoje)
        esperar("a atualização terminar") { vm.state.value.salvo }

        val depois = gravadas().single()
        assertEquals(id, depois.id)
        assertEquals(-22_00L, depois.amountCents)
    }

    @Test
    fun `editar nao move o lancamento para hoje`() {
        val id = runBlocking {
            db.txnDao().insert(
                LANCAMENTO.copy(
                    accountId = carteira,
                    categoryId = alimentacao,
                    date = dia(2026, 3, 3),
                ),
            )
        }

        vm.editar(id)
        esperar("a transação carregar na folha") { vm.state.value.editando }
        vm.descricao("Padaria")
        vm.salvar(hoje)
        esperar("a atualização terminar") { vm.state.value.salvo }

        // `hoje` é 10 de março. Corrigir a descrição não pode mudar o dia em que
        // o lançamento conta — nem o mês em que ele entra no relatório.
        assertEquals(dia(2026, 3, 3), gravadas().single().date)
    }

    @Test
    fun `parcela abre editavel, com o grupo carregado e o escopo no menor estrago`() {
        val id = runBlocking {
            db.txnDao().insert(
                LANCAMENTO.copy(
                    accountId = carteira,
                    categoryId = alimentacao,
                    installmentGroupId = "grupo-1",
                    installmentIndex = 3,
                    installmentTotal = 12,
                ),
            )
        }

        vm.editar(id)
        esperar("a transação carregar na folha") { vm.state.value.editando }

        // A T-050 abria isto somente leitura por não ter quem perguntasse o
        // escopo; a T-027 pergunta (REQ-TXN-009). O padrão é o menor estrago.
        assertTrue(vm.state.value.ehParcela)
        assertEquals(EscopoDeParcela.SO_ESTA, vm.state.value.escopo)
        // O campo de parcelas some ao editar: parcelar é da criação.
        assertFalse(vm.state.value.mostraParcelas)
    }

    private fun gravadas() =
        runBlocking { db.txnDao().observeBetween(dia(2026, 1, 1), dia(2026, 12, 31)).first() }

}
