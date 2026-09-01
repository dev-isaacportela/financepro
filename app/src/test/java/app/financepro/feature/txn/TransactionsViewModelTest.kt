package app.financepro.feature.txn

import androidx.lifecycle.SavedStateHandle
import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIA
import app.financepro.data.db.CONTA
import app.financepro.data.db.DbTest
import app.financepro.data.db.LANCAMENTO
import app.financepro.data.db.dia
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.TxnRepository
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.Filtro
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

/**
 * REQ-TXN-010 · REQ-TXN-011 · REQ-TXN-012
 *
 * Fino de propósito: a regra da lista é provada em JVM pura no `TxnListTest`, e
 * a preservação das colunas no desfazer, no `RepositoryTest`. O que sobra para
 * cá é a orquestração — o que a tela vê depois de cada ação.
 */
@Req("REQ-TXN-010", "REQ-TXN-011", "REQ-TXN-012")
class TransactionsViewModelTest : DbTest() {

    private lateinit var vm: TransactionsViewModel
    private var corrente = 0L
    private var poupanca = 0L
    private var mercado = 0L

    @Before
    fun montar() {
        runBlocking {
            corrente = db.accountDao().upsert(CONTA.copy(name = "Corrente", initialBalanceCents = 100_000))
            poupanca = db.accountDao().upsert(CONTA.copy(name = "Poupança", type = AccountType.SAVINGS))
            mercado = db.categoryDao().upsert(CATEGORIA.copy(name = "Mercado"))
            db.txnDao().insert(
                LANCAMENTO.copy(
                    accountId = corrente,
                    categoryId = mercado,
                    amountCents = -1_850,
                    date = dia(2026, 3, 10),
                    description = "Padaria",
                ),
            )
            db.txnDao().insert(
                LANCAMENTO.copy(
                    accountId = corrente,
                    categoryId = mercado,
                    amountCents = -3_000,
                    date = dia(2026, 3, 12),
                    description = "Feira",
                ),
            )
        }
        vm = TransactionsViewModel(
            AccountRepository(db.accountDao()),
            CategoryRepository(db.categoryDao(), db.txnDao()),
            TxnRepository(db.txnDao()),
            // Os argumentos neutros de `Transacoes`, que é como a aba
            // navega. A tela filtrada da T-033 passa outros.
            SavedStateHandle(mapOf("categoriaId" to 0L, "mesIso" to "")),
        )
        esperar("as transações chegarem") { vm.state.value.todas.size == 2 }
        vm.irPara(YearMonth.of(2026, 3))
    }

    @Test
    fun `agrupa por dia, o mais recente primeiro`() {
        val dias = vm.state.value.dias

        assertEquals(2, dias.size)
        assertEquals(listOf("Feira", "Padaria"), dias.flatMap { d -> d.itens.map { it.description } })
        assertEquals(-3_000L, dias.first().totalCents)
    }

    @Test
    fun `mes sem lancamento fica vazio, e voltar ao mes traz de volta`() {
        vm.irPara(YearMonth.of(2026, 4))
        assertEquals(emptyList<Any>(), vm.state.value.dias)

        vm.irPara(YearMonth.of(2026, 3))
        assertEquals(2, vm.state.value.dias.size)
    }

    @Test
    fun `excluir tira da lista, e desfazer traz de volta`() {
        val alvo = vm.state.value.visiveis.first { it.description == "Padaria" }

        vm.excluir(alvo)
        esperar("a exclusão chegar na lista") { vm.state.value.todas.size == 1 }
        assertNull(runBlocking { db.txnDao().byId(alvo.id) })
        // O contador é o que reinicia o efeito do snackbar na tela.
        assertEquals(1, vm.state.value.exclusoes)

        vm.desfazer()
        esperar("o desfazer chegar na lista") { vm.state.value.todas.size == 2 }
        assertEquals("Padaria", runBlocking { db.txnDao().byId(alvo.id) }?.description)
    }

    @Test
    fun `filtrar por conta liga o saldo corrente, e sem conta ele nao existe`() {
        assertEquals(emptyMap<Long, Long>(), vm.state.value.saldos)

        vm.aplicar(Filtro(contaId = corrente))

        // 100.000 − 1.850 − 3.000, e a linha de cima é a mais recente.
        val saldos = vm.state.value.saldos
        val maisRecente = vm.state.value.visiveis.first { it.description == "Feira" }
        assertEquals(95_150L, saldos[maisRecente.id])
    }

    @Test
    fun `o saldo corrente inclui o que veio antes do mes exibido`() {
        // A armadilha do extrato: recortar o mês antes de acumular daria um
        // saldo que não bate com nenhum extrato de banco.
        runBlocking {
            db.txnDao().insert(
                LANCAMENTO.copy(
                    accountId = corrente,
                    categoryId = mercado,
                    amountCents = -50_000,
                    date = dia(2026, 1, 5),
                    description = "Janeiro",
                ),
            )
        }
        esperar("a linha de janeiro chegar") { vm.state.value.todas.size == 3 }
        vm.aplicar(Filtro(contaId = corrente))

        val maisAntiga = vm.state.value.visiveis.first { it.description == "Padaria" }

        // 100.000 − 50.000 (janeiro, fora da janela) − 1.850.
        assertEquals(48_150L, vm.state.value.saldos[maisAntiga.id])
    }

    @Test
    fun `busca e filtro de tipo atravessam ate a lista`() {
        vm.aplicar(Filtro(busca = "18,50"))
        assertEquals(listOf("Padaria"), vm.state.value.visiveis.map { it.description })

        vm.aplicar(Filtro(tipo = TxnType.INCOME))
        assertEquals(emptyList<String>(), vm.state.value.visiveis.map { it.description })

        vm.limparFiltros()
        assertEquals(2, vm.state.value.visiveis.size)
    }

    @Test
    fun `tudo ignora o mes`() {
        runBlocking {
            db.txnDao().insert(
                LANCAMENTO.copy(accountId = poupanca, date = dia(2025, 7, 1), description = "Antiga"),
            )
        }
        esperar("a linha antiga chegar") { vm.state.value.todas.size == 3 }

        assertEquals(2, vm.state.value.visiveis.size)
        vm.todoOPeriodo(true)
        assertEquals(3, vm.state.value.visiveis.size)
    }
}
