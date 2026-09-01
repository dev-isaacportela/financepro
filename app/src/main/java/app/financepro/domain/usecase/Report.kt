package app.financepro.domain.usecase

import app.financepro.core.time.MonthRange
import app.financepro.domain.model.Txn
import java.time.YearMonth

/**
 * Os três relatórios. REQ-RPT-001 · REQ-RPT-002 · REQ-RPT-003 · Art. 9
 *
 * Nenhum deles inventa uma regra nova: a pizza reusa [agruparPorCategoria], que
 * a fatura de cartão já usava, e a evolução reusa [comparativoDe], que o
 * dashboard já usava. É o que garante que "quanto gastei em setembro" dê o
 * mesmo número nas três telas — três somas escritas à mão divergiriam, e a que
 * estaria certa seria a que ninguém testou.
 *
 * **Transferência fica de fora dos três**, e não por um `filter` repetido em
 * cada um: o corte é `efeitoGlobal(txn) < 0`, e transferência vale **zero** ali
 * por construção (ADR-003) — mover R$ 1.000 da corrente para a poupança não
 * tira R$ 1.000 do patrimônio. Filtrar por `type != TRANSFER` daria o mesmo
 * resultado hoje e erraria no dia em que aparecesse outro tipo com contrapartida.
 *
 * **Previsto também fica de fora** (`cleared = 0`, REQ-TXN-006), igual ao saldo
 * e ao comparativo: relatório que conta a conta de luz que ainda não foi paga
 * responde outra pergunta.
 */

/** Quantos períodos a evolução mostra. REQ-RPT-002 */
const val MESES_DA_EVOLUCAO = 12

/** Quantas despesas a lista traz. REQ-RPT-003 */
const val MAIORES_DESPESAS = 10

/** As despesas do período, agrupadas e da maior para a menor. REQ-RPT-001 */
fun despesasPorCategoria(txns: List<Txn>, periodo: MonthRange): List<GrupoDeCategoria> =
    agruparPorCategoria(despesasDo(txns, periodo))

/**
 * Receitas e despesas dos últimos [meses] períodos, o mais antigo primeiro.
 * REQ-RPT-002
 *
 * Um [comparativoDe] por mês: doze chamadas sobre a mesma lista, em vez de um
 * agrupamento próprio. O custo é uma passada por mês numa lista que já está em
 * memória, e o ganho é a evolução não poder discordar do dashboard sobre o que
 * é receita.
 */
fun evolucaoMensal(
    txns: List<Txn>,
    ate: YearMonth,
    meses: Int = MESES_DA_EVOLUCAO,
): List<PontoMensal> = (meses - 1 downTo 0).map { atras ->
    val mes = ate.minusMonths(atras.toLong())
    val comparativo = comparativoDe(txns, mes)
    PontoMensal(mes, comparativo.receitasCents, comparativo.despesasCents)
}

/**
 * As [quantas] maiores despesas do período. REQ-RPT-003
 *
 * Ordena por `amountCents` **crescente** porque despesa é negativa: a maior
 * saída é o número mais baixo. Um `sortedByDescending { abs(it) }` daria o
 * mesmo e passaria a incluir receitas grandes no dia em que o filtro mudasse.
 */
fun maioresDespesas(
    txns: List<Txn>,
    periodo: MonthRange,
    quantas: Int = MAIORES_DESPESAS,
): List<Txn> = despesasDo(txns, periodo).sortedBy { it.amountCents }.take(quantas)

/** Um mês da evolução. [despesasCents] é negativo, como no banco. */
data class PontoMensal(
    val mes: YearMonth,
    val receitasCents: Long,
    val despesasCents: Long,
) {
    val liquidoCents: Long get() = receitasCents + despesasCents
}

/** O corte comum aos três relatórios — ver o cabeçalho do arquivo. */
private fun despesasDo(txns: List<Txn>, periodo: MonthRange): List<Txn> =
    txns.filter { it.cleared && it.date in periodo && efeitoGlobal(it) < 0 }
