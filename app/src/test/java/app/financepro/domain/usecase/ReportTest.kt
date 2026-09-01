package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.core.time.monthRange
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * REQ-RPT-001 · REQ-RPT-002 · REQ-RPT-003
 *
 * Os três relatórios erram do mesmo jeito, e é por isso que o teste é um só: a
 * transferência que vira despesa, e a prevista que vira gasto. Um relatório que
 * discorda do dashboard sobre quanto se gastou no mês é o tipo de divergência
 * que o Art. 3 chama de bug, e ela não aparece na tela — aparece na conta de
 * quem confia nela.
 */
@Req("REQ-RPT-001", "REQ-RPT-002", "REQ-RPT-003")
class ReportTest {

    private val marco = YearMonth.of(2026, 3)
    private val periodo = monthRange(marco)

    private fun dia(d: Int, mes: Int = 3) = LocalDate.of(2026, mes, d)

    private fun despesa(
        id: Long,
        cents: Long,
        data: LocalDate = dia(10),
        categoria: Long? = 10,
        cleared: Boolean = true,
    ) = Txn(
        id = id,
        accountId = 1,
        type = TxnType.EXPENSE,
        amountCents = -cents,
        date = data,
        categoryId = categoria,
        cleared = cleared,
    )

    private fun receita(id: Long, cents: Long, data: LocalDate = dia(5)) = Txn(
        id = id,
        accountId = 1,
        type = TxnType.INCOME,
        amountCents = cents,
        date = data,
        categoryId = 20,
    )

    /** Transferência é **uma** linha, com `counterAccountId` (ADR-003). */
    private fun transferencia(id: Long, cents: Long, d: Int = 12) = Txn(
        id = id,
        accountId = 1,
        type = TxnType.TRANSFER,
        amountCents = -cents,
        date = dia(d),
        counterAccountId = 2,
    )

    // ---------- REQ-RPT-001: pizza ----------

    @Test
    fun `transferencia nao entra na pizza`() {
        val fatias = despesasPorCategoria(
            listOf(despesa(1, 30_00), transferencia(2, 1_000_00)),
            periodo,
        )

        assertEquals(1, fatias.size)
        assertEquals(30_00L, fatias.single().totalCents)
    }

    @Test
    fun `prevista nao entra na pizza`() {
        val fatias = despesasPorCategoria(
            listOf(despesa(1, 30_00), despesa(2, 500_00, cleared = false)),
            periodo,
        )

        assertEquals(30_00L, fatias.single().totalCents)
    }

    @Test
    fun `agrupa por categoria, da maior para a menor, com o total positivo`() {
        val fatias = despesasPorCategoria(
            listOf(
                despesa(1, 30_00, categoria = 10),
                despesa(2, 20_00, categoria = 10),
                despesa(3, 90_00, categoria = 11),
            ),
            periodo,
        )

        assertEquals(listOf(11L, 10L), fatias.map { it.categoriaId })
        assertEquals(listOf(90_00L, 50_00L), fatias.map { it.totalCents })
    }

    @Test
    fun `fora do periodo nao entra`() {
        val fatias = despesasPorCategoria(
            listOf(despesa(1, 30_00), despesa(2, 700_00, dia(10, 2))),
            periodo,
        )

        assertEquals(30_00L, fatias.single().totalCents)
    }

    // ---------- REQ-RPT-002: evolução ----------

    @Test
    fun `a evolucao traz doze meses, do mais antigo ao mes pedido`() {
        val pontos = evolucaoMensal(emptyList(), marco)

        assertEquals(MESES_DA_EVOLUCAO, pontos.size)
        assertEquals(YearMonth.of(2025, 4), pontos.first().mes)
        assertEquals(marco, pontos.last().mes)
    }

    @Test
    fun `receita e despesa caem no mes certo, e a transferencia em nenhum`() {
        val pontos = evolucaoMensal(
            listOf(
                receita(1, 4_000_00, dia(5, 2)),
                despesa(2, 1_500_00),
                transferencia(3, 900_00),
            ),
            marco,
        )
        val fevereiro = pontos.single { it.mes == YearMonth.of(2026, 2) }
        val marcoPonto = pontos.single { it.mes == marco }

        assertEquals(4_000_00L, fevereiro.receitasCents)
        assertEquals(0L, fevereiro.despesasCents)
        assertEquals(0L, marcoPonto.receitasCents)
        // −1.500,00 e não −2.400,00: a transferência de 900 não é despesa.
        assertEquals(-1_500_00L, marcoPonto.despesasCents)
        assertEquals(-1_500_00L, marcoPonto.liquidoCents)
    }

    @Test
    fun `a evolucao concorda com o comparativo do dashboard`() {
        // O invariante que justifica a evolução reusar `comparativoDe` em vez de
        // agrupar por conta própria: dois números para "quanto entrou em março"
        // divergiriam, e o errado seria o que ninguém confere.
        val txns = listOf(receita(1, 3_000_00), despesa(2, 800_00), transferencia(3, 500_00))
        val ponto = evolucaoMensal(txns, marco).last()
        val comparativo = comparativoDe(txns, marco)

        assertEquals(comparativo.receitasCents, ponto.receitasCents)
        assertEquals(comparativo.despesasCents, ponto.despesasCents)
    }

    // ---------- REQ-RPT-003: maiores despesas ----------

    @Test
    fun `as maiores sao as dez de maior valor, da maior para a menor`() {
        val txns = (1..15L).map { despesa(it, it * 10_00) }

        val maiores = maioresDespesas(txns, periodo)

        assertEquals(MAIORES_DESPESAS, maiores.size)
        assertEquals(-150_00L, maiores.first().amountCents)
        assertEquals(-60_00L, maiores.last().amountCents)
    }

    @Test
    fun `a maior transferencia do mes nao aparece entre as maiores despesas`() {
        val maiores = maioresDespesas(
            listOf(despesa(1, 30_00), transferencia(2, 5_000_00)),
            periodo,
        )

        assertEquals(listOf(1L), maiores.map { it.id })
    }

    @Test
    fun `menos de dez despesas devolve o que houver`() {
        val maiores = maioresDespesas(listOf(despesa(1, 30_00)), periodo)

        assertTrue(maiores.size < MAIORES_DESPESAS)
        assertEquals(1, maiores.size)
    }
}
