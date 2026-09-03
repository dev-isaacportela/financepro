package app.financepro.feature

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CATEGORIA
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import app.financepro.feature.budget.BudgetState
import app.financepro.feature.card.CardState
import app.financepro.feature.home.HomeState
import app.financepro.feature.investments.InvestmentsState
import app.financepro.feature.reports.ReportsState
import app.financepro.feature.txn.TransactionsState
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * REQ-PERF-001 — valor derivado é calculado uma vez por emissão.
 *
 * O teste compara por **identidade**, e não por igualdade: `assertEquals` passa
 * com as duas implementações, porque recalcular devolve um valor igual. Só o
 * `assertSame` distingue "calculou uma vez e guardou" de "calculou de novo", que
 * é a diferença inteira desta regra.
 *
 * É o que cai no dia em que alguém devolver um `by lazy` para `get()`, e essa é a
 * única razão de ele existir. Não mede tempo: teste de relógio em CI compartilhado
 * é intermitente, e a propriedade que interessa aqui não é a duração — é o número
 * de vezes que o histórico é percorrido.
 *
 * Só propriedade de referência entra. `saldoCents` e `dividaCents` são `Long`, e
 * `assertSame` sobre primitivo não afirma nada; quem as protege é morarem no
 * mesmo arquivo das que estão aqui.
 */
@Req("REQ-PERF-001")
class EstadoDerivadoTest {

    private val conta = UMA_CONTA.copy(id = 1, name = "Corrente")
    private val cartao = UMA_CONTA.copy(
        id = 2,
        name = "Cartão",
        type = AccountType.CREDIT_CARD,
        closingDay = 10,
        dueDay = 17,
    )
    private val investimento =
        UMA_CONTA.copy(id = 3, name = "CDB", type = AccountType.INVESTMENT)
    private val categoria = UMA_CATEGORIA.copy(id = 1, name = "Alimentação")
    private val mes = YearMonth.of(2026, 8)
    private val hoje = LocalDate.of(2026, 8, 15)

    /** Trinta dias de despesa, que é o que uma tela real tem para percorrer. */
    private val txns = (1..30).map { dia ->
        Txn(
            id = dia.toLong(),
            accountId = if (dia % 3 == 0) cartao.id else conta.id,
            type = TxnType.EXPENSE,
            amountCents = -(dia * 100L),
            date = LocalDate.of(2026, 8, dia),
            categoryId = categoria.id,
        )
    }

    @Test
    fun `dashboard calcula cada derivado uma vez`() {
        val estado = HomeState(
            contas = listOf(conta, cartao),
            categorias = listOf(categoria),
            txns = txns,
            mes = mes,
            hoje = hoje,
        )

        // `ultimas` é o caso que mais dói: ordena as trinta para mostrar cinco.
        assertSame(estado.ultimas, estado.ultimas)
        assertSame(estado.proximas, estado.proximas)
        assertSame(estado.cartoes, estado.cartoes)
        assertSame(estado.comparativo, estado.comparativo)
    }

    @Test
    fun `lista de transacoes calcula a cadeia uma vez`() {
        val estado = TransactionsState(
            mes = mes,
            contas = listOf(conta, cartao),
            categorias = listOf(categoria),
            todas = txns,
        )

        // A cadeia inteira: `dias` depende de `visiveis`, que depende de
        // `doPeriodo`, que depende de `periodo`. Sem cache, uma leitura de `dias`
        // percorre a lista quatro vezes.
        assertSame(estado.periodo, estado.periodo)
        assertSame(estado.doPeriodo, estado.doPeriodo)
        assertSame(estado.visiveis, estado.visiveis)
        assertSame(estado.dias, estado.dias)
        assertSame(estado.saldos, estado.saldos)
    }

    @Test
    fun `relatorios calcula as fatias uma vez`() {
        val estado = ReportsState(mes = mes, categorias = listOf(categoria), todas = txns)

        // `totalCents` e `vazio` liam `fatias`, então uma passada da tela
        // agrupava as despesas por categoria três vezes.
        assertSame(estado.fatias, estado.fatias)
        assertSame(estado.evolucao, estado.evolucao)
        assertSame(estado.maiores, estado.maiores)
    }

    @Test
    fun `fatura e orcamento calculam uma vez`() {
        val fatura = CardState(
            mes = mes,
            hoje = hoje,
            cartao = cartao,
            contas = listOf(conta, cartao),
            categorias = listOf(categoria),
            todas = txns,
        )
        assertSame(fatura.grupos, fatura.grupos)

        val orcamento = BudgetState(
            mes = mes,
            hoje = hoje,
            categorias = listOf(categoria),
            todas = txns,
        )
        assertSame(orcamento.periodo, orcamento.periodo)
        assertSame(orcamento.progresso, orcamento.progresso)
        assertSame(orcamento.semTeto, orcamento.semTeto)
    }

    @Test
    fun `investimentos calcula as linhas uma vez`() {
        val estado = InvestmentsState(
            contas = listOf(conta, investimento),
            todas = txns,
            mes = mes,
        )

        // `totalCents` e `lancadoNoMesCents` liam `linhas` cada um.
        assertSame(estado.investimentos, estado.investimentos)
        assertSame(estado.linhas, estado.linhas)
        assertSame(estado.serie, estado.serie)
    }
}
