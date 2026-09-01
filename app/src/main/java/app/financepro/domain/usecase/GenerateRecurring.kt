package app.financepro.domain.usecase

import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import java.time.LocalDate

/**
 * Materialização de lançamentos recorrentes.
 *
 * REQ-REC-003 · REQ-REC-004 · REQ-REC-005 ·
 * [ADR-006](../../../../../../../../docs/decisoes.md)
 *
 * A aritmética de calendário é da [occurrences] (T-030). Aqui entram as duas
 * coisas que ela de propósito não sabe: até onde materializar, e o que já foi
 * materializado.
 *
 * Puro, como manda o Art. 9: quem escreve no banco é o `RecurringRepository`, e
 * é ele quem lê o relógio. [hoje] chega por parâmetro pela mesma razão de
 * `budgetProgress` — o horizonte depende do dia, e ler `LocalDate.now()` aqui
 * tornaria o teste dependente de quando roda.
 */

/**
 * Até onde o futuro é materializado. REQ-REC-004
 *
 * 60 dias cobre "próximas contas" e a projeção do mês seguinte. Gerar o futuro
 * inteiro é impossível — regra sem `endDate` não termina — e gerar 10 anos
 * enche o banco de linhas que ninguém olha.
 */
const val HORIZONTE_DIAS = 60L

/**
 * A regra, com o molde da transação que ela repete. REQ-REC-001
 *
 * Carrega o [spec] em vez de repetir `frequency`, `interval` e as âncoras: são
 * os mesmos campos, e duplicá-los aqui seria a chance de a regra e a expansão
 * discordarem sobre o dia do mês.
 *
 * [lastGeneratedDate] é a memória da idempotência (REQ-REC-003): a data da
 * última ocorrência já materializada. Nula em regra recém-criada, e é o que faz
 * a primeira geração começar em `spec.startDate`.
 */
data class RecurringRule(
    val id: Long = 0,
    val accountId: Long,
    val type: TxnType,
    val amountCents: Long,
    val description: String,
    val spec: RecurrenceSpec,
    val counterAccountId: Long? = null,
    val categoryId: Long? = null,
    val autoPost: Boolean = false,
    val active: Boolean = true,
    val lastGeneratedDate: LocalDate? = null,
)

/**
 * As ocorrências que faltam materializar. REQ-REC-003 · REQ-REC-004
 *
 * Vazia quando não há nada novo, e é isso que torna a geração idempotente: o
 * filtro é `> lastGeneratedDate`, então rodar de novo no mesmo dia não devolve
 * nada. Sem ele, quem abre o app três vezes ganha três aluguéis.
 *
 * O `takeWhile` vem **antes** do filtro porque [occurrences] é infinita em
 * regra sem `endDate`: é o horizonte que a termina, e filtrar uma sequência
 * infinita antes de limitá-la não retorna.
 */
fun pendingOccurrences(rule: RecurringRule, hoje: LocalDate): List<LocalDate> {
    if (!rule.active) return emptyList()
    val horizonte = hoje.plusDays(HORIZONTE_DIAS)
    val ultima = rule.lastGeneratedDate
    return occurrences(rule.spec)
        .takeWhile { it <= horizonte }
        .filter { ultima == null || it > ultima }
        .toList()
}

/**
 * A transação daquela data. REQ-REC-005
 *
 * `cleared = autoPost` é o requisito inteiro: regra automática nasce efetivada
 * e já mexe no saldo; regra manual nasce prevista e aparece em "próximas
 * contas" esperando confirmação (REQ-REC-008).
 *
 * O vínculo com a regra (`recurringRuleId`) é coluna de entidade, não de
 * domínio — quem grava é que o preenche, como faz com `dedupeKey`.
 */
fun RecurringRule.occurrenceAt(date: LocalDate) = Txn(
    accountId = accountId,
    type = type,
    amountCents = amountCents,
    date = date,
    counterAccountId = counterAccountId,
    categoryId = categoryId,
    description = description,
    cleared = autoPost,
)

/**
 * A próxima data em que a regra cai, a partir de [hoje]. REQ-REC-008
 *
 * Nula quando a regra já terminou — `endDate` no passado —, e é o que a lista
 * de recorrências exibe como "Encerrada" em vez de uma data inventada.
 *
 * Não é o mesmo que a primeira de [pendingOccurrences]: aquela responde "o que
 * falta materializar", que já pulou tudo até `lastGeneratedDate`. Esta responde
 * "quando cai de novo", e uma ocorrência já materializada continua sendo a
 * próxima até acontecer.
 */
fun RecurringRule.nextOccurrence(hoje: LocalDate): LocalDate? =
    occurrences(spec).dropWhile { it < hoje }.firstOrNull()

/** Janela de "próximas contas", em dias. REQ-REC-008 */
const val PROXIMAS_DIAS = 7L

/**
 * As contas previstas da semana. REQ-REC-008 · REQ-TXN-006
 *
 * `cleared = 0` e nada mais: a origem não importa: uma recorrência sem
 * `autoPost` e um lançamento previsto digitado à mão são a mesma promessa de
 * pagamento, e separá-los faria o bloco mentir sobre o que vem por aí.
 *
 * A janela começa **em hoje**, como o requisito diz. Conta vencida e não paga
 * fica de fora do bloco — ela continua na lista de transações, onde o filtro de
 * previstas a alcança.
 */
fun proximasContas(txns: List<Txn>, hoje: LocalDate): List<Txn> =
    txns.filter { !it.cleared && it.date >= hoje && it.date <= hoje.plusDays(PROXIMAS_DIAS) }
        .sortedWith(compareBy({ it.date }, { it.id }))
