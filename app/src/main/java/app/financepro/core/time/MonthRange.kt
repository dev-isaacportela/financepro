package app.financepro.core.time

import java.time.LocalDate
import java.time.YearMonth

/** Maior dia que qualquer mês pode ter. */
private const val MAX_DAY_OF_MONTH = 31

/**
 * Período mensal do app. REQ-CORE-003.
 *
 * Existe um único conceito de "mês" em todo o app — dashboard, orçamento e
 * relatórios derivam daqui. Quem recebe no dia 5 configura `monthStartDay = 5`
 * e o mês passa a ir de 05/mm a 04/mm+1.
 *
 * `java.time` direto, sem `Calendar` nem `Date`: `minSdk 26` já traz a API
 * nativa, sem desugaring (arquitetura.md §1).
 */
data class MonthRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean =
        date >= start && date <= endInclusive

    /** Dias no período. Varia com o mês, e com `monthStartDay` perto do fim. */
    val lengthInDays: Int
        get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()
}

/**
 * Período do mês de referência.
 *
 * | `monthStartDay` | mês       | período                     |
 * |-----------------|-----------|-----------------------------|
 * | 1               | 2026-08   | 2026-08-01 a 2026-08-31     |
 * | 5               | 2026-08   | 2026-08-05 a 2026-09-04     |
 * | 31              | 2026-02   | 2026-02-28 a 2026-03-30     |
 *
 * A terceira linha é a que exige o clamp: dia 31 em fevereiro cai no último dia
 * do mês. Diferente do cartão (REQ-CARD-002), onde o intervalo é restrito a
 * 1–28 na entrada e o clamp não precisa existir, aqui ele é obrigatório —
 * o usuário legitimamente escolhe 31.
 */
fun monthRange(month: YearMonth, monthStartDay: Int = 1): MonthRange {
    require(monthStartDay in 1..MAX_DAY_OF_MONTH) {
        "monthStartDay deve estar entre 1 e $MAX_DAY_OF_MONTH, recebido $monthStartDay"
    }
    val start = periodStart(month, monthStartDay)
    val nextStart = periodStart(month.plusMonths(1), monthStartDay)
    return MonthRange(start, nextStart.minusDays(1))
}

private fun periodStart(month: YearMonth, monthStartDay: Int): LocalDate =
    month.atDay(minOf(monthStartDay, month.lengthOfMonth()))
