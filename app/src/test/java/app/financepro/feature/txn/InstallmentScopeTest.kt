package app.financepro.feature.txn

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
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.EscopoDeParcela
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-TXN-009 — só o escopo escolhido muda.
 *
 * O teste vai até o **banco**, e não até a função pura: a regra de escopo é
 * fácil de acertar isolada e fácil de perder no caminho, entre o ViewModel que
 * escolhe os alvos e o repositório que grava. O erro que ele existe para pegar é
 * "escolhi só esta e mudaram as três", que só aparece depois da escrita.
 *
 * Três parcelas de R$ 300, em março, abril e maio.
 */
@Req("REQ-TXN-009")
class InstallmentScopeTest : DbTest() {

    private lateinit var txns: TxnRepository
    private lateinit var vm: QuickEntryViewModel
    private var cartao = 0L

    private val hoje = LocalDate.of(2026, 3, 10)
    private val alimentacao get() = CATEGORIAS_PADRAO.first { it.nome == "Alimentação" }.id
    private val lazer get() = CATEGORIAS_PADRAO.first { it.nome == "Lazer" }.id

    @Before
    fun montar() {
        SeedCallback.onCreate(db.openHelper.writableDatabase)
        txns = TxnRepository(db.txnDao())
        runBlocking {
            cartao = db.accountDao().upsert(
                CONTA.copy(
                    name = "Nubank",
                    type = AccountType.CREDIT_CARD,
                    creditLimitCents = 5_000_00,
                    closingDay = 10,
                    dueDay = 20,
                ),
            )
            txns.salvarParcelado(
                (1..3).map { n ->
                    Txn(
                        accountId = cartao,
                        type = TxnType.EXPENSE,
                        amountCents = -300_00,
                        date = hoje.plusMonths((n - 1).toLong()),
                        categoryId = alimentacao,
                        description = "Tênis",
                        installmentIndex = n,
                        installmentTotal = 3,
                    )
                },
            )
        }
        vm = QuickEntryViewModel(
            AccountRepository(db.accountDao()),
            CategoryRepository(db.categoryDao(), db.txnDao()),
            txns,
        )
        esperar("contas e categorias chegarem ao estado") {
            vm.state.value.contas.isNotEmpty() && vm.state.value.categorias.isNotEmpty()
        }
    }

    /** As três, em ordem de parcela. */
    private fun grupo() = runBlocking {
        db.txnDao().observeBetween(dia(2026, 1, 1), dia(2026, 12, 31)).first()
            .map { it.toDomain() }
            .sortedBy { it.installmentIndex }
    }

    private fun editarSegunda(escopo: EscopoDeParcela, valorCents: Long = 350_00) {
        val segunda = grupo().first { it.installmentIndex == 2 }
        vm.editar(segunda.id)
        esperar("a parcela carregar com o grupo") { vm.state.value.grupo.size == 3 }
        vm.escopo(escopo)
        vm.valor(valorCents)
        vm.categoria(lazer)
        vm.salvar(hoje)
        esperar("a gravação terminar") { vm.state.value.salvo }
    }

    @Test
    fun `so esta muda uma parcela e deixa as irmas intactas`() {
        editarSegunda(EscopoDeParcela.SO_ESTA)

        assertEquals(listOf(-300_00L, -350_00L, -300_00L), grupo().map { it.amountCents })
        assertEquals(listOf(alimentacao, lazer, alimentacao), grupo().map { it.categoryId })
    }

    @Test
    fun `esta e as futuras alcanca a partir da posicao, e nao mexe na anterior`() {
        editarSegunda(EscopoDeParcela.ESTA_E_FUTURAS)

        assertEquals(listOf(-300_00L, -350_00L, -350_00L), grupo().map { it.amountCents })
        // A primeira é a prova: se o escopo vazasse, ela mudaria junto.
        assertEquals(alimentacao, grupo().first().categoryId)
    }

    @Test
    fun `todas alcanca o grupo inteiro`() {
        editarSegunda(EscopoDeParcela.TODAS)

        assertEquals(listOf(-350_00L, -350_00L, -350_00L), grupo().map { it.amountCents })
        assertEquals(listOf(lazer, lazer, lazer), grupo().map { it.categoryId })
    }

    @Test
    fun `editar com escopo todas nao colapsa as datas das parcelas`() {
        editarSegunda(EscopoDeParcela.TODAS)

        // O jeito silencioso de destruir uma compra parcelada: propagar a data
        // junto com o valor deixaria as três no mesmo dia, e o espaçamento de um
        // mês de REQ-TXN-007 sumiria sem nenhum erro na tela.
        assertEquals(
            listOf(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 10)),
            grupo().map { it.date },
        )
        // E o grupo continua sendo um grupo, com as posições de sempre.
        assertEquals(listOf(1, 2, 3), grupo().map { it.installmentIndex })
        assertEquals(1, grupo().map { it.installmentGroupId }.distinct().size)
    }

    @Test
    fun `editar parcela preserva as colunas que o dominio nao carrega`() {
        val segunda = grupo().first { it.installmentIndex == 2 }
        runBlocking {
            val linha = db.txnDao().byId(segunda.id)!!
            db.txnDao().upsert(linha.copy(notes = "com o cupom", dedupeKey = "OFX-7"))
        }

        editarSegunda(EscopoDeParcela.SO_ESTA)

        val depois = runBlocking { db.txnDao().byId(segunda.id)!! }
        assertEquals("com o cupom", depois.notes)
        assertEquals("OFX-7", depois.dedupeKey)
    }

    @Test
    fun `excluir esta e as futuras leva duas, e o desfazer repoe as duas`() {
        val lista = TransactionsViewModel(
            AccountRepository(db.accountDao()),
            CategoryRepository(db.categoryDao(), db.txnDao()),
            txns,
        )
        esperar("a lista carregar as três parcelas") { lista.state.value.todas.size == 3 }
        val segunda = grupo().first { it.installmentIndex == 2 }

        // Parcela não some no deslize: abre a pergunta e nada é escrito ainda.
        lista.excluir(segunda)
        assertEquals(segunda.id, lista.state.value.excluindo?.id)
        assertEquals(3, grupo().size)

        lista.excluirComEscopo(EscopoDeParcela.ESTA_E_FUTURAS)
        esperar("a exclusão terminar") { grupo().size == 1 }
        assertEquals(listOf(1), grupo().map { it.installmentIndex })

        lista.desfazer()
        esperar("o desfazer repor as duas") { grupo().size == 3 }
        // Repõe as **duas**, e com os ids de antes: um desfazer que devolvesse
        // uma só seria pior que nenhum.
        assertEquals(listOf(1, 2, 3), grupo().map { it.installmentIndex })
    }
}
