package app.financepro.domain.usecase

import app.financepro.core.time.monthRange
import app.financepro.domain.model.Txn
import java.time.YearMonth

/**
 * O comparativo do período do dashboard. REQ-UI-004 · Art. 7 · Art. 9
 *
 * Três números que o usuário lê de relance — entrou, saiu, e se o mês está
 * melhor ou pior que o anterior. Regra, e não SQL, pelo mesmo motivo dos
 * irmãos: as duas formas de errar aqui são somar transferência como despesa e
 * contar previsto como gasto, e as duas são testáveis em JVM.
 *
 * A operação vem antes do tipo neste arquivo pela mesma razão que
 * `SplitInstallments.kt` e `ValidateTxn.kt` abrem com uma constante: o
 * `MatchingDeclarationName` do detekt exigiria que o arquivo se chamasse
 * `Comparativo.kt` se a data class fosse a primeira declaração — e o nome do
 * arquivo aqui é o da regra, como em `AccountBalance.kt` e `CardInvoice.kt`.
 */

/**
 * REQ-UI-004 — receitas e despesas de [mes], e o líquido do mês anterior.
 *
 * **Transferência vale zero**, via [efeitoGlobal] — a mesma regra de
 * [agruparPorDia] e de REQ-RPT-001. R$ 1.000 saindo da corrente para a poupança
 * não é despesa de R$ 1.000; o dinheiro não saiu do patrimônio.
 *
 * **Previsto não entra** (`cleared = 0`, REQ-TXN-006), igual a [balanceOf]. Na
 * F0 a distinção é teórica porque nada cria transação prevista antes da T-031,
 * mas o dashboard e o saldo respondendo coisas diferentes para "quanto gastei"
 * seria a divergência que o Art. 3 chama de bug.
 *
 * A separação é por **sinal do efeito**, não por [Txn.type]: uma linha `INCOME`
 * com valor negativo é estorno, e contá-la como receita inflaria os dois lados.
 * Assim o invariante do Art. 7 vale por construção — `receitas + despesas` é a
 * soma dos mesmos efeitos, só particionada.
 *
 * ponytail: [monthStartDay] chega como parâmetro mas ninguém passa outro valor
 * na F0 — não existe onde configurá-lo. Fica no lugar certo desde já porque
 * REQ-CORE-003 já define o conceito, e o dia em que a preferência existir isto
 * não muda.
 */
fun comparativoDe(txns: List<Txn>, mes: YearMonth, monthStartDay: Int = 1): Comparativo {
    val periodo = monthRange(mes, monthStartDay)
    val anterior = monthRange(mes.minusMonths(1), monthStartDay)

    val efeitos = txns
        .filter { it.cleared && it.date in periodo }
        .map { efeitoGlobal(it) }

    return Comparativo(
        receitasCents = efeitos.sumOf { maxOf(it, 0L) },
        despesasCents = efeitos.sumOf { minOf(it, 0L) },
        anteriorCents = txns
            .filter { it.cleared && it.date in anterior }
            .sumOf { efeitoGlobal(it) },
    )
}

/**
 * [receitasCents] é positivo e [despesasCents] negativo, como no banco — o par
 * soma para o líquido em vez de precisar de subtração, que é onde o sinal se
 * inverte por engano.
 *
 * [anteriorCents] é o **líquido** do período anterior, não o par dele: o bloco
 * mostra a variação, e guardar receita e despesa do mês passado seria carregar
 * dois números que ninguém exibe.
 */
data class Comparativo(
    val receitasCents: Long,
    val despesasCents: Long,
    val anteriorCents: Long,
) {
    val liquidoCents: Long get() = receitasCents + despesasCents

    /** Positivo: sobrou mais que no período anterior. */
    val deltaCents: Long get() = liquidoCents - anteriorCents
}
