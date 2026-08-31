package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** REQ-UI-004 — o comparativo do período do dashboard. */
@Req("REQ-UI-004")
class PeriodSummaryTest {

    private val mes = YearMonth.of(2026, 8)
    private val dia = LocalDate.of(2026, 8, 15)
    private val diaAnterior = LocalDate.of(2026, 7, 15)

    private fun receita(cents: Long, data: LocalDate = dia, cleared: Boolean = true) =
        Txn(accountId = 1, type = TxnType.INCOME, amountCents = cents, date = data, cleared = cleared)

    private fun despesa(cents: Long, data: LocalDate = dia, cleared: Boolean = true) =
        Txn(accountId = 1, type = TxnType.EXPENSE, amountCents = -cents, date = data, cleared = cleared)

    private fun transferencia(cents: Long, data: LocalDate = dia) =
        Txn(
            accountId = 1, counterAccountId = 2, type = TxnType.TRANSFER,
            amountCents = -cents, date = data,
        )

    @Test
    fun `receitas e despesas do periodo`() {
        val c = comparativoDe(listOf(receita(300000), despesa(50000), despesa(20000)), mes)

        assertEquals(300000L, c.receitasCents)
        assertEquals(-70000L, c.despesasCents)
        assertEquals(230000L, c.liquidoCents)
    }

    @Test
    fun `dinheiro nao some nem aparece`() {
        // O invariante do Art. 7: particionar por sinal não pode alterar a soma.
        val txns = listOf(
            receita(123456), despesa(7), despesa(999999),
            transferencia(500000), receita(1),
        )
        val c = comparativoDe(txns, mes)

        val efeitos = txns.sumOf { efeitoGlobal(it) }
        assertEquals(efeitos, c.receitasCents + c.despesasCents)
        assertEquals(efeitos, c.liquidoCents)
    }

    @Test
    fun `transferencia nao entra em nenhum dos dois lados`() {
        // R$ 1.000 mudando de bolso não é despesa de R$ 1.000. Somar
        // `amountCents` cru daria -100000 aqui, e o mês pareceria no prejuízo.
        val c = comparativoDe(listOf(transferencia(100000)), mes)

        assertEquals(0L, c.receitasCents)
        assertEquals(0L, c.despesasCents)
        assertEquals(0L, c.liquidoCents)
    }

    @Test
    fun `previsto fica fora`() {
        // REQ-TXN-006 — mesma regra de balanceOf. O dashboard e o saldo não
        // podem responder coisas diferentes para "quanto gastei".
        val c = comparativoDe(listOf(despesa(50000, cleared = false), despesa(10000)), mes)

        assertEquals(-10000L, c.despesasCents)
    }

    @Test
    fun `estorno entra pelo sinal, nao pelo tipo`() {
        // `INCOME` de valor negativo é estorno de receita. Classificar por
        // `type` o contaria como receita e inflaria os dois lados do bloco.
        val c = comparativoDe(listOf(receita(100000), receita(-30000)), mes)

        assertEquals(100000L, c.receitasCents)
        assertEquals(-30000L, c.despesasCents)
        assertEquals(70000L, c.liquidoCents)
    }

    @Test
    fun `delta compara com o mes anterior`() {
        val txns = listOf(
            receita(300000), despesa(100000),                          // agosto: +200000
            receita(300000, diaAnterior), despesa(180000, diaAnterior), // julho:  +120000
        )
        val c = comparativoDe(txns, mes)

        assertEquals(120000L, c.anteriorCents)
        assertEquals(200000L, c.liquidoCents)
        assertEquals(80000L, c.deltaCents)
    }

    @Test
    fun `mes anterior vazio nao inventa base`() {
        // Primeiro mês de uso: o delta é o líquido inteiro, não uma divisão por
        // zero nem um "sem dados" que a tela teria de tratar à parte.
        val c = comparativoDe(listOf(receita(50000)), mes)

        assertEquals(0L, c.anteriorCents)
        assertEquals(50000L, c.deltaCents)
    }

    @Test
    fun `periodo recorta pelas bordas`() {
        val txns = listOf(
            despesa(1000, LocalDate.of(2026, 7, 31)),
            despesa(2000, LocalDate.of(2026, 8, 1)),
            despesa(4000, LocalDate.of(2026, 8, 31)),
            despesa(8000, LocalDate.of(2026, 9, 1)),
        )
        val c = comparativoDe(txns, mes)

        assertEquals(-6000L, c.despesasCents)
        assertEquals(-1000L, c.anteriorCents)
    }
}
