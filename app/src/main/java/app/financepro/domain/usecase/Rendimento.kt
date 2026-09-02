package app.financepro.domain.usecase

import app.financepro.core.time.monthRange
import app.financepro.domain.model.Account
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import java.time.YearMonth

/**
 * Rendimento de investimento. REQ-INV-002 · REQ-INV-004 · Art. 6 · Art. 16
 *
 * Inteiro puro, como todo caminho de dinheiro: a taxa chega pronta, em partes
 * por milhão, de `core/taxa/Taxa.kt` — é lá que mora o único ponto flutuante do
 * módulo, e o KDoc de lá explica por quê.
 *
 * **Nada aqui inventa como um lançamento é classificado.** Numa conta de
 * investimento o tipo da transação já separa tudo, sem coluna nem flag nova:
 * `TRANSFER` entrando é aporte, `TRANSFER` saindo é resgate, `INCOME` é
 * rendimento e `EXPENSE` é taxa ou imposto. Aporte e resgate já funcionavam
 * pelo lançamento rápido antes deste módulo existir.
 *
 * Tudo é por **mês de referência**, e não "agora": o rendimento de agosto só se
 * conhece quando agosto fecha, e quem abre o app em setembro está lançando o
 * mês passado. Uma função que só soubesse o mês corrente não teria como.
 *
 * ponytail: `monthRange` com o dia 1 fixo, como nas outras quatro telas —
 * trocar quando `monthStartDay` virar preferência de verdade.
 */

/** 100% em partes por milhão, a escala que `mensalPpm` produz. */
private const val BASE_PPM = 1_000_000L

/**
 * Quanto [saldoCents] rende em um mês a [taxaMensalPpm]. REQ-INV-002
 *
 * Arredonda para o centavo mais próximo, meio para cima: `+ BASE_PPM / 2` antes
 * da divisão inteira, que é o truque padrão para não truncar sempre para baixo
 * — truncar custaria até um centavo por mês, sempre no mesmo sentido, e o
 * previsto ficaria sistematicamente abaixo do extrato.
 *
 * Saldo negativo ou taxa zerada devolvem zero em vez de um número: investimento
 * no vermelho é conta errada, não prejuízo a lançar como receita.
 *
 * Limite conhecido: `saldoCents * taxaMensalPpm` estoura `Long` acima de ~92
 * bilhões de reais. O app não é para esse patrimônio.
 */
fun rendimentoPrevisto(saldoCents: Long, taxaMensalPpm: Int): Long {
    if (saldoCents <= 0 || taxaMensalPpm <= 0) return 0
    return (saldoCents * taxaMensalPpm + BASE_PPM / 2) / BASE_PPM
}

/** O saldo da conta no último dia de [mes]. Reusa [balanceOf], não o refaz. */
fun saldoAoFimDe(txns: List<Txn>, conta: Account, mes: YearMonth): Long {
    val fim = monthRange(mes).endInclusive
    return balanceOf(conta, txns.filter { it.date <= fim })
}

/** O rendimento já lançado em [mes]. Zero quando ainda não houve. REQ-INV-003 */
fun rendimentoDe(txns: List<Txn>, conta: Account, mes: YearMonth): Long =
    somaEm(txns, conta, mes, TxnType.INCOME)

/** O aporte líquido de [mes] — resgate entra negativo. REQ-INV-004 */
fun aporteDe(txns: List<Txn>, conta: Account, mes: YearMonth): Long =
    somaEm(txns, conta, mes, TxnType.TRANSFER)

/**
 * O rendimento que [mes] deveria ter dado. REQ-INV-002
 *
 * A base é o saldo no fim do **mês anterior**, não o de hoje: é o dinheiro que
 * passou o mês rendendo. Usar o saldo atual faria o previsto de agosto mudar
 * toda vez que um aporte fosse feito em setembro — e faria o previsto de um mês
 * passado ser recalculado para cima sem que nada naquele mês tivesse mudado.
 *
 * ponytail: saldo do início do mês, não saldo médio diário. Um aporte no dia 10
 * rende os 20 dias restantes na vida real e nenhum dia aqui. Trocar quando
 * aporte no meio do mês incomodar — a conta é a mesma, ponderada por dia.
 */
fun previstoDe(txns: List<Txn>, conta: Account, mes: YearMonth, taxaMensalPpm: Int): Long =
    rendimentoPrevisto(saldoAoFimDe(txns, conta, mes.minusMonths(1)), taxaMensalPpm)

/**
 * Um mês da série de acompanhamento. REQ-INV-004
 *
 * [aporteCents] é **líquido**: resgate entra negativo, porque é o mesmo
 * `efeitoEm` que o saldo usa e inverter o sinal aqui criaria uma segunda
 * convenção para a mesma transferência.
 */
data class MesDeInvestimento(
    val mes: YearMonth,
    val rendimentoCents: Long,
    val aporteCents: Long,
    val saldoFimCents: Long,
)

/**
 * Os últimos [meses] meses de [contas] somadas, o mais antigo primeiro.
 * REQ-INV-004
 *
 * Reusa [efeitoEm] e [balanceOf] de `AccountBalance.kt` pela mesma razão que
 * `evolucaoMensal` reusa `comparativoDe`: o saldo do fim de agosto tem que dar
 * o mesmo número aqui e na lista de contas, e duas somas escritas à mão
 * divergem — a errada sendo a que ninguém testou.
 *
 * Recebe a lista inteira de transações, não a das contas: [efeitoEm] devolve
 * zero para o que não é delas, e um `filter` antes seria uma segunda definição
 * de "é desta conta" convivendo com a do saldo.
 */
fun serieSomada(
    txns: List<Txn>,
    contas: List<Account>,
    ate: YearMonth,
    meses: Int = MESES_DA_EVOLUCAO,
): List<MesDeInvestimento> = (meses - 1 downTo 0).map { atras ->
    val mes = ate.minusMonths(atras.toLong())
    MesDeInvestimento(
        mes = mes,
        rendimentoCents = contas.sumOf { rendimentoDe(txns, it, mes) },
        aporteCents = contas.sumOf { aporteDe(txns, it, mes) },
        saldoFimCents = contas.sumOf { saldoAoFimDe(txns, it, mes) },
    )
}

/** A série de uma conta só. Uma linha, para não haver duas implementações. */
fun serieMensal(
    txns: List<Txn>,
    conta: Account,
    ate: YearMonth,
    meses: Int = MESES_DA_EVOLUCAO,
): List<MesDeInvestimento> = serieSomada(txns, listOf(conta), ate, meses)

private fun somaEm(txns: List<Txn>, conta: Account, mes: YearMonth, tipo: TxnType): Long {
    val periodo = monthRange(mes)
    return txns
        .filter { it.cleared && it.type == tipo && it.date in periodo }
        .sumOf { efeitoEm(it, conta.id) }
}
