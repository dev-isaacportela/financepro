package com.benenutri.finance.domain.usecase

import java.time.LocalDate

/**
 * Divisão de compra parcelada.
 *
 * REQ-TXN-007 · REQ-TXN-008 · Art. 7 · [ADR-005](../../../../../../../../docs/decisoes.md)
 *
 * Uma compra em N vezes vira **N transações gravadas na criação**, não uma
 * linha expandida sob demanda. A alternativa parece mais enxuta até se contar
 * os lugares que precisariam expandir: fatura de daqui a 4 meses, orçamento do
 * mês que vem, projeção de fluxo, relatório de 12 meses, filtro por categoria.
 */

/** Faixa aceita de parcelas. REQ-TXN-007. */
val INSTALLMENT_RANGE = 1..72

/** Uma parcela do plano. `index` é 1-based, como o usuário lê ("3 de 10"). */
data class Installment(
    val index: Int,
    val count: Int,
    val amountCents: Long,
    val date: LocalDate,
) {
    /** Rótulo que aparece na lista e na fatura. */
    val label: String get() = "$index de $count"
}

/**
 * Divide [totalCents] em [count] parcelas mensais a partir de [firstDate].
 *
 * **A soma das parcelas é exatamente igual ao total, sempre.** A sobra de
 * arredondamento vai toda na **última** parcela, nunca na primeira — quem
 * confere o extrato compara a primeira parcela com o valor anunciado na compra.
 *
 * ```
 * parcela[i] = total / count            para i = 1..count-1
 * parcela[n] = total - (count-1) * (total / count)
 * ```
 *
 * | Total    | count | Parcelas          | Soma     |
 * |----------|-------|-------------------|----------|
 * | 60000    | 7     | 6× 8571 + 1× 8574 | 60000    |
 * | 10       | 3     | 2× 3 + 1× 4       | 10       |
 * | 100000   | 1     | 1× 100000         | 100000   |
 * | 1        | 2     | 1× 0 + 1× 1       | 1        |
 *
 * Funciona igual para valor negativo, que é como despesa é gravada
 * (REQ-TXN-002): a divisão inteira do Kotlin trunca em direção a zero, então a
 * sobra continua caindo na última parcela.
 *
 * Datas usam `plusMonths`, que já ajusta fim de mês: compra em 31/01 gera a
 * segunda parcela em 28/02.
 */
fun splitInstallments(
    totalCents: Long,
    count: Int,
    firstDate: LocalDate,
): List<Installment> {
    require(count in INSTALLMENT_RANGE) {
        "parcelas deve estar entre 1 e 72, recebido $count (REQ-TXN-007)"
    }
    require(totalCents != 0L) { "valor não pode ser zero (REQ-CORE-002)" }

    val base = totalCents / count
    val last = totalCents - base * (count - 1)

    return (1..count).map { i ->
        Installment(
            index = i,
            count = count,
            amountCents = if (i == count) last else base,
            date = firstDate.plusMonths((i - 1).toLong()),
        )
    }
}
