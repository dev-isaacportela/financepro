package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.data.repo.RecurringRepository
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.Frequency
import app.financepro.domain.usecase.HORIZONTE_DIAS
import app.financepro.domain.usecase.RecurrenceSpec
import app.financepro.domain.usecase.RecurringRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-REC-003 · REQ-REC-004 · REQ-REC-005 · REQ-REC-007 — materialização.
 *
 * Roda no banco, e não em JVM pura, porque é exatamente aí que estão os erros:
 * a idempotência mora no `lastGeneratedDate` gravado, e o histórico imutável
 * mora no que o `DELETE` deixa em paz. As datas em si já são da
 * `RecurrenceExpansionTest`, sem banco.
 */
@Req("REQ-REC-003", "REQ-REC-004", "REQ-REC-005", "REQ-REC-007")
class GenerateRecurringTest : DbTest() {

    private val hoje = LocalDate.of(2026, 9, 1)

    // `lazy` e não `@Before`: cada teste roda numa instância nova, e a conta só
    // nasce quando a primeira regra a pede — uma por teste, não uma por regra.
    private val conta by lazy { runBlocking { db.accountDao().upsert(CONTA) } }
    private val categoria by lazy { runBlocking { db.categoryDao().upsert(CATEGORIA) } }

    private fun repo() = RecurringRepository(db.recurringDao())

    private fun lancamentos() = runBlocking {
        db.txnDao().observeBetween(dia(2000, 1, 1), dia(2100, 1, 1)).first()
    }

    private fun datas() = lancamentos().map { LocalDate.ofEpochDay(it.date) }.sorted()

    /** Uma regra completa; o teste varia por `copy` o que lhe interessa. */
    private fun regra(
        frequency: Frequency = Frequency.MONTHLY,
        inicio: LocalDate = LocalDate.of(2026, 6, 10),
        autoPost: Boolean = false,
        valor: Long = -1_200_00,
    ) = RecurringRule(
        accountId = conta,
        categoryId = categoria,
        type = TxnType.EXPENSE,
        amountCents = valor,
        description = "Aluguel",
        spec = RecurrenceSpec(frequency = frequency, startDate = inicio),
        autoPost = autoPost,
    )

    @Test
    fun `tres execucoes no mesmo dia produzem o mesmo conjunto`() = runBlocking {
        val repo = repo()
        repo.salvar(regra(), hoje)
        val primeira = lancamentos()

        assertEquals(0, repo.gerarPendentes(hoje))
        assertEquals(0, repo.gerarPendentes(hoje))

        // Os mesmos ids, não só a mesma quantidade: uma geração que apagasse e
        // recriasse tudo daria a mesma contagem e quebraria toda referência.
        assertTrue("a primeira execução não gerou nada", primeira.isNotEmpty())
        assertEquals(primeira, lancamentos())
    }

    @Test
    fun `nada e materializado alem de hoje mais 60 dias`() = runBlocking {
        // Diária a partir de hoje: 61 linhas, de hoje até o horizonte, inclusive
        // nos dois extremos. Sem `endDate` — é o caso que o requisito nomeia.
        repo().salvar(regra(frequency = Frequency.DAILY, inicio = hoje), hoje)

        assertEquals(hoje, datas().first())
        assertEquals(hoje.plusDays(HORIZONTE_DIAS), datas().last())
        assertEquals(HORIZONTE_DIAS + 1, datas().size.toLong())
    }

    @Test
    fun `autoPost decide se a ocorrencia nasce efetivada`() = runBlocking {
        val repo = repo()
        repo.salvar(regra(autoPost = true), hoje)
        assertTrue("autoPost = true deveria nascer cleared", lancamentos().all { it.cleared })

        db.txnDao().deleteAll(lancamentos())
        repo.salvar(regra(autoPost = false), hoje)
        assertTrue("autoPost = false deveria nascer prevista", lancamentos().none { it.cleared })
    }

    @Test
    fun `alterar a regra reescreve o futuro previsto e nao toca o efetivado`() = runBlocking {
        val repo = repo()
        val id = repo.salvar(regra(), hoje)

        // Julho vira histórico: foi pago, e o valor dele é o que estava valendo.
        val julho = lancamentos().single { it.date == dia(2026, 7, 10) }
        db.txnDao().upsert(julho.copy(cleared = true))

        repo.salvar(regra(valor = -1_500_00).copy(id = id), hoje)

        val porData = lancamentos().associate { LocalDate.ofEpochDay(it.date) to it.amountCents }
        // Passado — efetivado ou não — fica como estava. Reescrever o que já
        // venceu seria o app decidir que a conta antiga custava outra coisa.
        assertEquals(-1_200_00L, porData[LocalDate.of(2026, 6, 10)])
        assertEquals(-1_200_00L, porData[LocalDate.of(2026, 7, 10)])
        assertEquals(-1_200_00L, porData[LocalDate.of(2026, 8, 10)])
        // Futuro previsto sai e volta pela regra nova.
        assertEquals(-1_500_00L, porData[LocalDate.of(2026, 9, 10)])
        assertEquals(-1_500_00L, porData[LocalDate.of(2026, 10, 10)])
        assertEquals(5, porData.size)
    }
}
