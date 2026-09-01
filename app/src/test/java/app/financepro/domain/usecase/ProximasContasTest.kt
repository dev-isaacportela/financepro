package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-REC-008 — o bloco "Próximas contas" e a data que a lista de recorrências
 * mostra.
 *
 * As duas regras que o dashboard não pode errar sozinho: **o que** entra na
 * janela, e **quando** a regra cai de novo. Nenhuma das duas precisa de banco,
 * e é por isso que o teste do bloco é este, e não um caminho de UI.
 */
@Req("REQ-REC-008")
class ProximasContasTest {

    private val hoje = LocalDate.of(2026, 9, 1)

    private fun d(ano: Int, mes: Int, dia: Int) = LocalDate.of(ano, mes, dia)

    private fun prevista(dia: LocalDate, id: Long = 0, cleared: Boolean = false) = Txn(
        id = id,
        accountId = 1,
        type = TxnType.EXPENSE,
        amountCents = -1_000,
        date = dia,
        categoryId = 1,
        cleared = cleared,
    )

    @Test
    fun `a janela e de hoje ate hoje mais sete, nos dois extremos`() {
        val txns = listOf(
            prevista(hoje.minusDays(1), id = 1),
            prevista(hoje, id = 2),
            prevista(hoje.plusDays(PROXIMAS_DIAS), id = 3),
            prevista(hoje.plusDays(PROXIMAS_DIAS + 1), id = 4),
        )

        // A vencida de ontem fica de fora: o requisito diz "próximos 7 dias", e
        // ela continua alcançável pelo filtro de previstas na lista.
        assertEquals(listOf(2L, 3L), proximasContas(txns, hoje).map { it.id })
    }

    @Test
    fun `efetivada nao e proxima conta`() {
        val txns = listOf(
            prevista(hoje.plusDays(1), id = 1, cleared = true),
            prevista(hoje.plusDays(1), id = 2),
        )

        assertEquals(listOf(2L), proximasContas(txns, hoje).map { it.id })
    }

    @Test
    fun `ordena por data, e por id dentro do mesmo dia`() {
        val txns = listOf(
            prevista(hoje.plusDays(3), id = 10),
            prevista(hoje.plusDays(1), id = 20),
            prevista(hoje.plusDays(1), id = 5),
        )

        assertEquals(listOf(5L, 20L, 10L), proximasContas(txns, hoje).map { it.id })
    }

    // ---------- a data que a lista de recorrências exibe ----------

    private fun regra(spec: RecurrenceSpec) = RecurringRule(
        accountId = 1,
        type = TxnType.EXPENSE,
        amountCents = -1_000,
        description = "Aluguel",
        spec = spec,
    )

    @Test
    fun `a proxima ocorrencia ignora o que ja foi materializado`() {
        // `lastGeneratedDate` em outubro é o que `pendingOccurrences` usaria
        // para pular tudo até lá. "Quando cai de novo" é outra pergunta: a de
        // setembro ainda não aconteceu, e é ela que a lista mostra.
        val comMarca = regra(RecurrenceSpec(Frequency.MONTHLY, d(2026, 6, 10)))
            .copy(lastGeneratedDate = d(2026, 10, 10))

        assertEquals(d(2026, 9, 10), comMarca.nextOccurrence(hoje))
    }

    @Test
    fun `regra encerrada nao tem proxima`() {
        val terminada = regra(
            RecurrenceSpec(Frequency.MONTHLY, d(2026, 1, 10), endDate = d(2026, 6, 10)),
        )

        assertNull(terminada.nextOccurrence(hoje))
    }

    @Test
    fun `ocorrencia de hoje conta como a proxima`() {
        val hojeMesmo = regra(RecurrenceSpec(Frequency.MONTHLY, d(2026, 6, 1)))

        assertEquals(hoje, hojeMesmo.nextOccurrence(hoje))
    }
}
