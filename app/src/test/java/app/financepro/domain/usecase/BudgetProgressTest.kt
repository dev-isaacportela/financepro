package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.core.time.monthRange
import app.financepro.domain.UMA_CATEGORIA
import app.financepro.domain.model.Budget
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * REQ-BUD-002 · REQ-BUD-004 — o que entra no consumo, e o que sobra por dia.
 *
 * Agosto de 2026, teto de R$ 600 em Alimentação, que tem "Delivery" por filha.
 */
@Req("REQ-BUD-002", "REQ-BUD-004")
class BudgetProgressTest {

    private val alimentacao = UMA_CATEGORIA.copy(id = 1, name = "Alimentação")
    private val delivery = UMA_CATEGORIA.copy(id = 2, name = "Delivery", parentId = alimentacao.id)
    private val transporte = UMA_CATEGORIA.copy(id = 3, name = "Transporte")
    private val categorias = listOf(alimentacao, delivery, transporte)

    private val agosto = YearMonth.of(2026, 8)
    private val teto = Budget(categoryId = alimentacao.id, month = agosto, limitCents = 600_00)

    private fun despesa(dia: LocalDate, cents: Long, categoriaId: Long) = Txn(
        accountId = 1,
        type = TxnType.EXPENSE,
        amountCents = -cents,
        date = dia,
        categoryId = categoriaId,
    )

    private fun progresso(
        txns: List<Txn>,
        hoje: LocalDate = LocalDate.of(2026, 8, 1),
        monthStartDay: Int = 1,
    ) = budgetProgress(listOf(teto), categorias, txns, monthRange(agosto, monthStartDay), hoje).single()

    @Test
    fun `despesa em subcategoria conta no teto da mae`() {
        val txns = listOf(
            despesa(LocalDate.of(2026, 8, 3), 100_00, alimentacao.id),
            despesa(LocalDate.of(2026, 8, 4), 50_00, delivery.id),
        )

        // Sem isto o teto vira decoração: bastaria lançar tudo na filha para
        // nunca estourar o teto da mãe.
        assertEquals(150_00L, progresso(txns).spentCents)
    }

    @Test
    fun `despesa em outra categoria nao conta`() {
        val txns = listOf(
            despesa(LocalDate.of(2026, 8, 3), 100_00, alimentacao.id),
            despesa(LocalDate.of(2026, 8, 4), 999_00, transporte.id),
        )

        assertEquals(100_00L, progresso(txns).spentCents)
    }

    @Test
    fun `transferencia nunca entra no consumo`() {
        val txns = listOf(
            despesa(LocalDate.of(2026, 8, 3), 100_00, alimentacao.id),
            // Uma transferência não tem categoria (REQ-TXN-004 a proíbe), mas o
            // filtro não pode depender disso: uma linha vinda de importação com
            // categoria preenchida ainda seria transferência, e mover dinheiro
            // entre bolsos não é gasto.
            Txn(
                accountId = 1,
                type = TxnType.TRANSFER,
                amountCents = -1_000_00,
                date = LocalDate.of(2026, 8, 5),
                counterAccountId = 2,
                categoryId = alimentacao.id,
            ),
        )

        assertEquals(100_00L, progresso(txns).spentCents)
    }

    @Test
    fun `estorno na categoria abate o consumo`() {
        val txns = listOf(
            despesa(LocalDate.of(2026, 8, 3), 100_00, alimentacao.id),
            Txn(
                accountId = 1,
                type = TxnType.INCOME,
                amountCents = 30_00,
                date = LocalDate.of(2026, 8, 6),
                categoryId = alimentacao.id,
            ),
        )

        assertEquals(70_00L, progresso(txns).spentCents)
    }

    @Test
    fun `periodo respeita monthStartDay`() {
        val txns = listOf(
            // Com `monthStartDay = 5`, agosto vai de 05/08 a 04/09.
            despesa(LocalDate.of(2026, 8, 2), 40_00, alimentacao.id),
            despesa(LocalDate.of(2026, 8, 10), 60_00, alimentacao.id),
            despesa(LocalDate.of(2026, 9, 2), 20_00, alimentacao.id),
        )

        // Mês do calendário: só as duas de agosto.
        assertEquals(100_00L, progresso(txns).spentCents)
        // Mês que começa no dia 5: a de 02/08 sai e a de 02/09 entra.
        assertEquals(80_00L, progresso(txns, monthStartDay = 5).spentCents)
    }

    @Test
    fun `sobra diaria divide o que resta pelos dias que faltam`() {
        val txns = listOf(despesa(LocalDate.of(2026, 8, 1), 290_00, alimentacao.id))

        // Restam R$ 310 e 31 dias contando hoje (01/08 a 31/08) — R$ 10 por dia.
        val p = progresso(txns, hoje = LocalDate.of(2026, 8, 1))
        assertEquals(31, p.diasRestantes)
        assertEquals(10_00L, p.sobraDiariaCents)

        // No dia 30 sobram dois dias, e a mesma folga rende mais por dia.
        assertEquals(155_00L, progresso(txns, hoje = LocalDate.of(2026, 8, 30)).sobraDiariaCents)
    }

    @Test
    fun `estourado mostra o excedido, e nao sobra diaria negativa`() {
        val txns = listOf(despesa(LocalDate.of(2026, 8, 3), 712_00, alimentacao.id))

        val p = progresso(txns)
        assertEquals(112_00L, p.estourouCents)
        // "Você pode gastar −R$ 3 por dia" não é uma frase que ajude alguém.
        assertEquals(0L, p.sobraDiariaCents)
        assertTrue(p.percent >= ESTOURO_PERCENT)
    }

    @Test
    fun `percentual acompanha o gasto, e as duas faixas de alerta`() {
        assertEquals(0, progresso(emptyList()).percent)
        assertEquals(
            ALERTA_PERCENT,
            progresso(listOf(despesa(LocalDate.of(2026, 8, 3), 480_00, alimentacao.id))).percent,
        )
        assertEquals(
            ESTOURO_PERCENT,
            progresso(listOf(despesa(LocalDate.of(2026, 8, 3), 600_00, alimentacao.id))).percent,
        )
    }

    @Test
    fun `dias restantes nao quebram a divisao em mes ja vencido ou futuro`() {
        val txns = listOf(despesa(LocalDate.of(2026, 8, 3), 100_00, alimentacao.id))

        // A tela navega meses: olhando agosto em dezembro, "restantes" seria
        // negativo e a divisão quebraria; olhando em janeiro, passaria do
        // tamanho do próprio período.
        assertEquals(1, progresso(txns, hoje = LocalDate.of(2026, 12, 1)).diasRestantes)
        assertEquals(31, progresso(txns, hoje = LocalDate.of(2026, 1, 1)).diasRestantes)
    }

    @Test
    fun `categoria sem teto nao aparece, e teto de outro mes tambem nao`() {
        val deJulho = Budget(categoryId = transporte.id, month = YearMonth.of(2026, 7), limitCents = 100_00)

        val lista = budgetProgress(
            listOf(teto, deJulho),
            categorias,
            emptyList(),
            monthRange(agosto),
            LocalDate.of(2026, 8, 1),
        )

        assertEquals(listOf(alimentacao.id), lista.map { it.categoria.id })
    }
}
