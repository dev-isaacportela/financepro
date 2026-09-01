package app.financepro.domain.usecase

import app.financepro.domain.model.Txn
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

/**
 * Escopo de edição e exclusão de parcela. REQ-TXN-009 · Art. 9
 *
 * Uma compra parcelada é um grupo de linhas irmãs (ADR-005), e mexer numa delas
 * sem perguntar o escopo deixa as outras onze inconsistentes — foi por isso que
 * a T-050 abriu parcela somente leitura em vez de adivinhar. Esta é a task que
 * pergunta.
 *
 * A escolha é do usuário; o que mora aqui é **quais linhas** cada escolha
 * alcança, e o que de uma edição atravessa para as irmãs. Regra, não tela.
 */
enum class EscopoDeParcela { SO_ESTA, ESTA_E_FUTURAS, TODAS }

/**
 * As parcelas que [escopo] alcança, a partir de [alvo].
 *
 * "Futuras" é pela **posição no grupo**, não pela data: `installmentIndex` é o
 * que ordena a compra parcelada, e comparar datas daria outro conjunto no dia em
 * que alguém corrigisse a data de uma parcela do meio. Sem índice — grupo
 * corrompido ou linha solta — só a própria linha é alcançada, que é a leitura
 * segura.
 */
fun parcelasNoEscopo(alvo: Txn, grupo: List<Txn>, escopo: EscopoDeParcela): List<Txn> = when (escopo) {
    EscopoDeParcela.SO_ESTA -> listOf(alvo)
    EscopoDeParcela.TODAS -> grupo.ifEmpty { listOf(alvo) }
    EscopoDeParcela.ESTA_E_FUTURAS -> {
        val posicao = alvo.installmentIndex
        if (posicao == null) {
            listOf(alvo)
        } else {
            grupo.filter { (it.installmentIndex ?: Int.MIN_VALUE) >= posicao }.ifEmpty { listOf(alvo) }
        }
    }
}

/**
 * Espalha a edição de [editada] sobre as parcelas de [alvos]. REQ-TXN-009
 *
 * O que **não** atravessa é o que é de cada parcela: `id`, `date` e a posição no
 * grupo. Propagar a data colapsaria as doze parcelas no mesmo dia — o oposto do
 * espaçamento de um mês que REQ-TXN-007 exige, e um jeito silencioso de destruir
 * uma compra parcelada inteira.
 *
 * O valor atravessa, e é a leitura literal de "aplicar a mudança ao escopo
 * escolhido": trocar a parcela para R$ 350 com escopo `TODAS` deixa doze de
 * R$ 350. Redividir um novo total entre as parcelas é outra operação, e nenhum
 * requisito a pede.
 */
fun aplicarNasParcelas(editada: Txn, alvos: List<Txn>): List<Txn> = alvos.map { alvo ->
    if (alvo.id == editada.id) {
        editada
    } else {
        alvo.copy(
            accountId = editada.accountId,
            categoryId = editada.categoryId,
            description = editada.description,
            amountCents = editada.amountCents,
            cleared = editada.cleared,
        )
    }
}
