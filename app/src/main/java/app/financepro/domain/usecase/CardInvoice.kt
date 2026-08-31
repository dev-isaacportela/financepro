package app.financepro.domain.usecase

import java.time.LocalDate
import java.time.YearMonth

/**
 * Competência e vencimento de fatura de cartão.
 *
 * REQ-CARD-003 · REQ-CARD-004 · [ADR-004](../../../../../../../../docs/decisoes.md)
 *
 * Não existe tabela `invoice`: a fatura é derivada destas duas funções. Fatura
 * materializada precisaria ser criada, fechada, reaberta quando o usuário edita
 * a data de uma compra antiga, e recalculada a cada importação — estado
 * derivado armazenado é estado que sai de sincronia.
 *
 * O agrupamento acontece em Kotlin e não em SQL porque SQLite não tem
 * aritmética de data decente, e replicar a regra num `@Query` criaria a segunda
 * fonte de verdade que o Art. 9 proíbe.
 */

/** Faixa válida para fechamento e vencimento. Ver [requireCardDay]. */
val CARD_DAY_RANGE = 1..28

/**
 * Bancos brasileiros não fecham fatura nos dias 29 a 31, então restringir a
 * entrada a 1–28 elimina a classe inteira de bugs de "dia 31 em fevereiro"
 * **sem uma linha de tratamento** — e sem perder caso real.
 *
 * É o oposto da recorrência (REQ-REC-006), onde o clamp é obrigatório porque
 * conta que vence dia 30 é comum.
 */
private fun requireCardDay(day: Int, nome: String) =
    require(day in CARD_DAY_RANGE) {
        "$nome deve estar entre 1 e 28, recebido $day (REQ-CARD-002)"
    }

/**
 * Mês da fatura em que a compra cai.
 *
 * Compra **até** o dia do fechamento, inclusive, entra na fatura que fecha
 * naquele mesmo mês; depois do fechamento, cai na seguinte.
 *
 * | `closingDay` | compra       | fatura    |
 * |--------------|--------------|-----------|
 * | 10           | 2026-03-09   | 2026-03   |
 * | 10           | 2026-03-10   | 2026-03   |
 * | 10           | 2026-03-11   | 2026-04   |
 * | 10           | 2026-12-15   | 2027-01   |
 */
fun invoiceMonthFor(purchaseDate: LocalDate, closingDay: Int): YearMonth {
    requireCardDay(closingDay, "closingDay")
    val month = YearMonth.from(purchaseDate)
    return if (purchaseDate.dayOfMonth <= closingDay) month else month.plusMonths(1)
}

/**
 * Vencimento da fatura que fecha em [invoiceMonth].
 *
 * Se o vencimento cai depois do fechamento, vence no próprio mês; senão, no
 * seguinte. `dueDay == closingDay` conta como "não depois", e portanto vence no
 * mês seguinte — um cartão não fecha e vence no mesmo dia.
 *
 * | `closingDay` | `dueDay` | fatura   | vencimento |
 * |--------------|----------|----------|------------|
 * | 10           | 20       | 2026-03  | 2026-03-20 |
 * | 20           | 10       | 2026-03  | 2026-04-10 |
 * | 10           | 10       | 2026-03  | 2026-04-10 |
 * | 25           | 5        | 2026-12  | 2027-01-05 |
 */
fun dueDateFor(invoiceMonth: YearMonth, closingDay: Int, dueDay: Int): LocalDate {
    requireCardDay(closingDay, "closingDay")
    requireCardDay(dueDay, "dueDay")
    val month = if (dueDay > closingDay) invoiceMonth else invoiceMonth.plusMonths(1)
    return month.atDay(dueDay)
}

/**
 * Data em que a fatura de [invoiceMonth] fecha.
 *
 * Sem clamp e sem surpresa: `CARD_DAY_RANGE` garante que o dia existe em todo
 * mês, inclusive fevereiro de ano não bissexto.
 */
fun closingDateFor(invoiceMonth: YearMonth, closingDay: Int): LocalDate {
    requireCardDay(closingDay, "closingDay")
    return invoiceMonth.atDay(closingDay)
}
