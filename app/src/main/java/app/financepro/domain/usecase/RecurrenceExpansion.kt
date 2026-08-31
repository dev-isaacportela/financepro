package app.financepro.domain.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cálculo das datas de um lançamento recorrente.
 *
 * REQ-REC-001 · REQ-REC-002 · REQ-REC-006 ·
 * [ADR-006](../../../../../../../../docs/decisoes.md)
 *
 * Só as **datas**. Materializar as transações, com idempotência e horizonte de
 * 60 dias, é a T-031 — separado de propósito: a aritmética de calendário é
 * testável sem banco, e é onde estão os erros difíceis.
 */

private const val MAX_DAY_OF_MONTH = 31
private const val MONTHS_PER_YEAR = 12
private const val DAYS_PER_WEEK = 7

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A regra, sem os campos de transação (valor, conta, categoria) — esses vivem
 * em `recurring_rule` e não influenciam as datas.
 *
 * Os campos de âncora são opcionais e, quando nulos, saem de [startDate]. É a
 * mesma convenção do iCalendar (`BYMONTHDAY` derivando de `DTSTART`), e evita
 * o estado inconsistente de `dayOfMonth = 10` com `startDate` no dia 3.
 */
data class RecurrenceSpec(
    val frequency: Frequency,
    val startDate: LocalDate,
    val interval: Int = 1,
    val endDate: LocalDate? = null,
    /** Dia do mês para MONTHLY e YEARLY. Padrão: o dia de [startDate]. */
    val dayOfMonth: Int? = null,
    /** Dia da semana para WEEKLY. Padrão: o dia da semana de [startDate]. */
    val weekday: DayOfWeek? = null,
    /** Mês para YEARLY. Padrão: o mês de [startDate]. */
    val monthOfYear: Int? = null,
) {
    init {
        require(interval >= 1) { "interval deve ser >= 1, recebido $interval" }
        dayOfMonth?.let { require(it in 1..MAX_DAY_OF_MONTH) { "dayOfMonth inválido: $it" } }
        monthOfYear?.let { require(it in 1..MONTHS_PER_YEAR) { "monthOfYear inválido: $it" } }
        endDate?.let {
            require(it >= startDate) { "endDate ($it) antes de startDate ($startDate)" }
        }
    }
}

/**
 * Datas de ocorrência, em ordem crescente, a partir de [RecurrenceSpec.startDate].
 *
 * Sequência preguiçosa e **infinita** quando não há `endDate` — quem consome
 * limita. É o que permite a T-031 materializar só até hoje + 60 dias sem que
 * esta função saiba o que é "horizonte".
 *
 * **O clamp de dia do mês é obrigatório aqui**, ao contrário do cartão
 * (REQ-CARD-002), porque conta que vence dia 30 ou 31 é comum:
 *
 * | `dayOfMonth` | mês       | data gerada |
 * |--------------|-----------|-------------|
 * | 31           | 2026-02   | 2026-02-28  |
 * | 31           | 2028-02   | 2028-02-29  |
 * | 31           | 2026-04   | 2026-04-30  |
 * | 30           | 2026-02   | 2026-02-28  |
 *
 * E o clamp **não gruda**: uma regra do dia 31 gera 28/02 e volta para 31/03,
 * em vez de ficar presa no dia 28. Por isso cada ocorrência é calculada a
 * partir da âncora original, nunca a partir da ocorrência anterior.
 */
fun occurrences(spec: RecurrenceSpec): Sequence<LocalDate> {
    val bruta = when (spec.frequency) {
        Frequency.DAILY -> generate { n -> spec.startDate.plusDays(n * spec.interval) }
        Frequency.WEEKLY -> weekly(spec)
        Frequency.MONTHLY -> monthly(spec)
        Frequency.YEARLY -> yearly(spec)
    }
    return bruta
        .dropWhile { it < spec.startDate }
        .takeWhile { spec.endDate == null || it <= spec.endDate }
}

private fun generate(at: (Long) -> LocalDate): Sequence<LocalDate> =
    generateSequence(0L) { it + 1 }.map(at)

private fun weekly(spec: RecurrenceSpec): Sequence<LocalDate> {
    val alvo = spec.weekday ?: spec.startDate.dayOfWeek
    // Primeira ocorrência: o próprio startDate se já cair no dia certo, senão
    // o próximo dia da semana alvo.
    val diasAte =
        ((alvo.value - spec.startDate.dayOfWeek.value) + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val primeira = spec.startDate.plusDays(diasAte.toLong())
    return generate { n -> primeira.plusWeeks(n * spec.interval) }
}

private fun monthly(spec: RecurrenceSpec): Sequence<LocalDate> {
    val ancora = spec.dayOfMonth ?: spec.startDate.dayOfMonth
    val primeiroMes = YearMonth.from(spec.startDate)
    return generate { n -> primeiroMes.plusMonths(n * spec.interval).clampTo(ancora) }
}

private fun yearly(spec: RecurrenceSpec): Sequence<LocalDate> {
    val ancora = spec.dayOfMonth ?: spec.startDate.dayOfMonth
    val mes = spec.monthOfYear ?: spec.startDate.monthValue
    val primeiroAno = YearMonth.of(spec.startDate.year, mes)
    return generate { n -> primeiroAno.plusYears(n * spec.interval).clampTo(ancora) }
}

/** Dia [day] deste mês, ou o último dia se ele não existir. REQ-REC-006. */
private fun YearMonth.clampTo(day: Int): LocalDate = atDay(minOf(day, lengthOfMonth()))
