package app.financepro.domain.usecase

import app.financepro.core.time.MonthRange
import app.financepro.domain.model.Budget
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Consumo de orçamento. REQ-BUD-002 · REQ-BUD-003 · REQ-BUD-004 · Art. 9
 *
 * O período chega pronto de `monthRange` (REQ-CORE-003), e não é "o mês do
 * calendário": quem recebe no dia 5 orça de 05/mm a 04/mm+1, e um orçamento que
 * fechasse em outro dia do que o dashboard mostra seriam duas verdades sobre o
 * mesmo mês.
 *
 * [hoje] é parâmetro pela mesma razão de `validateTxn`: a sobra diária depende
 * do dia, e ler o relógio aqui tornaria o teste dependente de quando roda.
 */

/** Percentual a partir do qual a barra avisa. REQ-BUD-003 */
const val ALERTA_PERCENT = 80

/** Percentual em que o teto estourou. REQ-BUD-003 */
const val ESTOURO_PERCENT = 100

/**
 * Um teto e o que já foi gasto contra ele.
 *
 * [spentCents] é **positivo**: é quanto saiu. As despesas são negativas
 * (REQ-TXN-002) e o sinal é invertido na composição, como em `cardDebt` e no
 * total da fatura — comparar teto com número negativo obrigaria toda tela e
 * todo teste a lembrar da convenção.
 */
data class BudgetProgress(
    val categoria: Category,
    val limitCents: Long,
    val spentCents: Long,
    val periodo: MonthRange,
    val hoje: LocalDate,
) {
    /**
     * Zero quando não há teto positivo. `BudgetRepository.definir` recusa teto
     * zero ou negativo, então isto só protege a divisão de dado vindo de fora
     * (importação, restauração) — e protege sem inventar um percentual.
     */
    val percent: Int
        get() = if (limitCents <= 0) 0 else ((spentCents * PERCENT) / limitCents).toInt().coerceAtLeast(0)

    /** Quanto passou do teto. Zero enquanto está dentro. REQ-BUD-004 */
    val estourouCents: Long get() = (spentCents - limitCents).coerceAtLeast(0)

    /**
     * Dias que ainda contam, incluindo hoje.
     *
     * Preso à faixa do período porque a tela navega meses: olhando um mês
     * futuro, "restantes" passaria do tamanho do próprio período; olhando um
     * mês vencido, seria zero ou negativo e a divisão quebraria.
     */
    val diasRestantes: Int
        get() = (periodo.endInclusive.toEpochDay() - hoje.toEpochDay() + 1).toInt()
            .coerceIn(1, periodo.lengthInDays)

    /**
     * Quanto ainda dá para gastar por dia até o fim do período. REQ-BUD-004
     *
     * Zero quando estourou: a spec pede o valor excedido no lugar, e não uma
     * sobra diária negativa — "você pode gastar −R$ 12 por dia" não é uma frase
     * que ajude alguém. Quem exibe usa [estourouCents].
     */
    val sobraDiariaCents: Long
        get() = if (estourouCents > 0) 0 else (limitCents - spentCents) / diasRestantes

    private companion object {
        const val PERCENT = 100L
    }
}

/**
 * O progresso de cada teto do mês. REQ-BUD-002
 *
 * Duas regras que o teste existe para travar:
 *
 * - **subcategoria conta no teto da mãe.** Orçar "Alimentação" e gastar em
 *   "Delivery" precisa consumir o teto de Alimentação, senão o teto vira
 *   decoração — basta lançar tudo numa filha para nunca estourar. A hierarquia é
 *   de um nível (REQ-CAT-002), então uma passada de `parentId` basta;
 * - **transferência nunca entra.** Mover R$ 1.000 da corrente para a poupança
 *   não é gasto, e contá-lo consumiria o teto de uma categoria que a
 *   transferência nem tem (REQ-TXN-004 a proíbe).
 *
 * Ordenado do mais apertado para o mais folgado: quem abre a tela quer ver
 * primeiro o que está prestes a estourar.
 */
fun budgetProgress(
    budgets: List<Budget>,
    categorias: List<Category>,
    txns: List<Txn>,
    periodo: MonthRange,
    hoje: LocalDate,
): List<BudgetProgress> {
    // O mês de referência sai do início do período, e não de um parâmetro à
    // parte: `monthRange(agosto, 5)` começa em 05/08 e `monthRange(fevereiro,
    // 31)` em 28/02 — nos dois, `YearMonth.from(start)` devolve o mês pedido.
    // Um segundo parâmetro seria a chance de a tela passar um mês que não é o do
    // período que ela mesma montou.
    val mes = YearMonth.from(periodo.start)
    val gastoPorCategoria = txns
        .filter { it.date in periodo && it.type != TxnType.TRANSFER }
        .groupBy { it.categoryId }
    val filhas = categorias.filter { it.parentId != null }.groupBy { it.parentId }

    return budgets
        .filter { it.month == mes }
        .mapNotNull { teto ->
            val categoria = categorias.firstOrNull { it.id == teto.categoryId }
            categoria?.let {
                val alcance = listOf(it.id) + filhas[it.id].orEmpty().map { filha -> filha.id }
                BudgetProgress(
                    categoria = it,
                    limitCents = teto.limitCents,
                    spentCents = -alcance.sumOf { id ->
                        gastoPorCategoria[id].orEmpty().sumOf { txn -> txn.amountCents }
                    },
                    periodo = periodo,
                    hoje = hoje,
                )
            }
        }
        .sortedByDescending { it.percent }
}
