package app.financepro.domain.usecase

import app.financepro.core.money.parseCents
import app.financepro.domain.model.Account
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import java.time.LocalDate
import kotlin.math.abs

/**
 * A lista de transações como regra, não como tela.
 *
 * REQ-TXN-011 · REQ-TXN-012 · REQ-ACC-005 · Art. 9 ·
 * [ADR-009](../../../../../../../../docs/decisoes.md)
 *
 * Agrupamento, total do dia, saldo corrente e filtro moram aqui porque são
 * regra, e regra não vive em ViewModel nem em `@Query` (Art. 9). O ganho
 * prático é que os três casos que dão errado — transferência no total do dia,
 * saldo corrente que ignora o passado, busca por valor com sinal trocado — são
 * testáveis em JVM, em milissegundos, sem emulador.
 *
 * **Nada disso é SQL de propósito.** O ADR-009 já decidiu que a lista carrega o
 * mês em memória (~100 linhas), e sobre isso um `WHERE (:x IS NULL OR col = :x)`
 * de cinco cláusulas custa mais para ler do que um `filter` — sem resolver o que
 * importa: o `LIKE` do SQLite só ignora caixa em ASCII.
 */

/**
 * Os quatro filtros de REQ-TXN-012, num objeto só.
 *
 * [busca] é um campo, não dois: quem procura digita `Padaria` ou `18,50` no
 * mesmo lugar e espera achar. Separar em "buscar texto" e "buscar valor" seria
 * pedir ao usuário que classifique a própria busca antes de fazê-la.
 */
data class Filtro(
    val contaId: Long? = null,
    val categoriaId: Long? = null,
    val tipo: TxnType? = null,
    val busca: String = "",
) {
    val ativo: Boolean
        get() = contaId != null || categoriaId != null || tipo != null || busca.isNotBlank()
}

/** Um dia da lista, com o total que o cabeçalho exibe (REQ-TXN-011). */
data class DiaDeTransacoes(val data: LocalDate, val totalCents: Long, val itens: List<Txn>)

/**
 * Um bloco da fatura ou do relatório: a categoria e o que ela pesa.
 *
 * [categoriaId] nulo é "sem categoria" — REQ-TXN-005 exige categoria em despesa
 * e receita, mas transferência não tem, e o grupo precisa existir para o total
 * fechar com a soma das partes (Art. 7).
 */
data class GrupoDeCategoria(val categoriaId: Long?, val totalCents: Long, val itens: List<Txn>)

/** Uma linha do extrato, com o saldo da conta **depois** dela. */
data class LinhaDeExtrato(val txn: Txn, val saldoCents: Long)

/**
 * REQ-TXN-012 — filtra por conta, categoria, tipo e busca.
 *
 * A conta casa também por `counterAccountId`: sem isso a transferência sumiria
 * do extrato de quem recebeu, e o usuário veria dinheiro entrar do nada
 * (ADR-003, mesma razão de `TxnDao.observeByAccount`).
 *
 * A busca por valor compara o **módulo**: quem digita `18,50` procura a despesa
 * de `−18,50`, e exigir o sinal seria pedir que a pessoa soubesse a convenção
 * interna do banco (REQ-TXN-002). A conversão sai de `parseCents`, a mesma e
 * única do projeto — uma segunda leitura de texto para centavos divergiria da
 * que a importação usa (Art. 6).
 *
 * ponytail: busca sensível a acento — `contains` não dobra diacrítico, então
 * "alimentacao" não acha "Alimentação". A dobra é da `normalize` da T-036, e
 * escrever uma segunda aqui criaria exatamente as duas implementações que
 * aquela task existe para impedir.
 */
fun filtrar(txns: List<Txn>, filtro: Filtro): List<Txn> {
    val texto = filtro.busca.trim()
    val cents = parseCents(texto)?.let { abs(it) }
    return txns.filter { txn ->
        (filtro.contaId == null || txn.accountId == filtro.contaId || txn.counterAccountId == filtro.contaId) &&
            (filtro.categoriaId == null || txn.categoryId == filtro.categoriaId) &&
            (filtro.tipo == null || txn.type == filtro.tipo) &&
            (texto.isEmpty() || casaBusca(txn, texto, cents))
    }
}

private fun casaBusca(txn: Txn, texto: String, cents: Long?): Boolean =
    txn.description.contains(texto, ignoreCase = true) ||
        (cents != null && abs(txn.amountCents) == cents)

/**
 * REQ-TXN-011 — agrupa por dia, em ordem decrescente, com o total de cada dia.
 *
 * Com [contaId], o total é o efeito naquela conta; sem ele, o efeito no
 * patrimônio somado — e aí **transferência entre contas próprias vale zero**.
 * Somar `amountCents` cru faria R$ 1.000 mudando de bolso parecer prejuízo de
 * R$ 1.000 no cabeçalho.
 *
 * ponytail: o total soma **as linhas visíveis**, previsto incluído — diferente
 * de `balanceOf`, que exclui `cleared = 0` (REQ-TXN-006). Um cabeçalho que não
 * bate com o que está logo abaixo lê como bug, e é o que aconteceria. Em F0 a
 * divergência é teórica: nada cria transação prevista antes da T-031. Separar
 * "total do dia" de "previsto do dia" quando a T-031 chegar.
 */
fun agruparPorDia(txns: List<Txn>, contaId: Long? = null): List<DiaDeTransacoes> =
    txns.groupBy { it.date }
        .entries
        .sortedByDescending { it.key }
        .map { (data, itens) ->
            DiaDeTransacoes(
                data = data,
                totalCents = itens.sumOf { if (contaId == null) efeitoGlobal(it) else efeitoEm(it, contaId) },
                itens = itens.sortedByDescending { it.id },
            )
        }

/**
 * Agrupa por categoria, do que mais pesa para o que menos pesa. REQ-CARD-006
 *
 * O total é **positivo** e a ordem é decrescente por ele: numa fatura, quem abre
 * a tela quer ver primeiro onde o dinheiro foi. Um estorno de categoria deixa o
 * grupo negativo e o empurra para o fim, que é onde ele deve estar.
 *
 * A soma dos grupos é, por construção, o total da fatura — o mesmo invariante
 * do Art. 7 que `splitInstallments` tem, e pela mesma razão.
 */
fun agruparPorCategoria(txns: List<Txn>): List<GrupoDeCategoria> =
    txns.groupBy { it.categoryId }
        .map { (categoriaId, itens) ->
            GrupoDeCategoria(
                categoriaId = categoriaId,
                totalCents = -itens.sumOf { it.amountCents },
                itens = itens.sortedByDescending { it.date },
            )
        }
        .sortedByDescending { it.totalCents }

/**
 * Extrato de [conta] com saldo corrente linha a linha. REQ-ACC-005
 *
 * Recebe o histórico **inteiro** da conta, não a janela exibida: o saldo da
 * primeira linha visível precisa incluir tudo que veio antes dela, e recortar o
 * mês antes de acumular daria um número que não bate com nenhum extrato. Quem
 * chama recorta **depois**.
 *
 * Acumula em ordem crescente e devolve decrescente — a linha de cima é a mais
 * recente, e traz o saldo mais recente. O último acumulado é, por construção,
 * o mesmo `balanceOf(conta, txns)`; é o invariante do Art. 7 e tem teste.
 */
fun extrato(conta: Account, txns: List<Txn>): List<LinhaDeExtrato> {
    var saldo = conta.initialBalanceCents
    return txns
        .sortedWith(compareBy({ it.date }, { it.id }))
        .map { txn ->
            if (txn.cleared) saldo += efeitoEm(txn, conta.id)
            LinhaDeExtrato(txn, saldo)
        }
        .reversed()
}
