package app.financepro.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Modelos de domínio. **Kotlin puro** — nenhuma anotação de Room, nenhum
 * import de `android.*` (Art. 8, arquitetura.md §3).
 *
 * É essa fronteira que permite testar saldo, validação, fatura e orçamento em
 * JVM, em milissegundos, sem emulador. No dia em que ela vazar, os testes ficam
 * lentos — e teste lento deixa de ser rodado.
 *
 * As entidades de Room são separadas e mapeiam para estes tipos (T-004).
 */

enum class AccountType { CHECKING, SAVINGS, CASH, CREDIT_CARD, INVESTMENT }

/**
 * A que o rendimento de um investimento está atrelado. REQ-INV-001
 *
 * Dois, e não uma tabela de índices: `PREFIXADO` é a taxa que o papel promete e
 * não depende de nada externo; `CDI` depende de um número que muda com a Selic e
 * vem de fora do app. IPCA+ e poupança seriam um terceiro caso — outra série a
 * buscar e outra linha na tela — e nenhum dos dois cobre CDB, LCI/LCA ou
 * prefixado, que é o que a maioria acompanha.
 */
enum class Indexador { PREFIXADO, CDI }

/**
 * REQ-ACC-001 · REQ-ACC-002 · REQ-CARD-001 · REQ-INV-001
 *
 * [taxaBp] é **pontos-base**, e o que ela mede depende de [indexador]:
 * `PREFIXADO` → taxa anual (`1250` = 12,50% a.a.); `CDI` → percentual do índice
 * (`11000` = 110% do CDI). Uma coluna com dois significados é deliberada — é
 * literalmente o número ao lado do indexador na tela, e duas colunas
 * mutuamente exclusivas custariam uma migração a mais para dizer o mesmo.
 *
 * [colorArgb] e [iconKey] são obrigatórios porque REQ-ACC-001 diz que toda conta
 * tem cor e ícone. Sem valor padrão, ninguém cria conta sem eles por descuido —
 * e o preço é que o teste de saldo precisa dizer uma cor que não lhe interessa,
 * o que é barato perto de uma conta invisível na lista.
 */
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val colorArgb: Int,
    val iconKey: String,
    val initialBalanceCents: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    // Só CREDIT_CARD:
    val creditLimitCents: Long? = null,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
    val paymentAccountId: Long? = null,
    // Só INVESTMENT (REQ-INV-001):
    val indexador: Indexador? = null,
    val taxaBp: Int? = null,
) {
    val isCard: Boolean get() = type == AccountType.CREDIT_CARD

    val isInvestimento: Boolean get() = type == AccountType.INVESTMENT
}

enum class CategoryKind { INCOME, EXPENSE }

/**
 * REQ-CAT-001 · REQ-CAT-002 · REQ-CAT-003
 *
 * [colorArgb] e [iconKey] são de apresentação, e sobem para o domínio porque o
 * grid do lançamento rápido é uma cartela de adesivos (design.md §6.2): cada
 * categoria já **é** um sticker com cor e ícone próprios. Continuam dados puros
 * — um `Int` e uma `String` —, sem nada de Compose atravessar a fronteira.
 */
data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val colorArgb: Int,
    val iconKey: String,
    val parentId: Long? = null,
    val archived: Boolean = false,
)

/**
 * O teto de uma categoria num mês. REQ-BUD-001
 *
 * [month] é `YearMonth` e não o `yyyyMM` inteiro da coluna: a conversão é de
 * borda, e nenhuma regra de orçamento precisa saber que o banco guarda 202608.
 */
data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val month: YearMonth,
    val limitCents: Long,
)

enum class TxnType { INCOME, EXPENSE, TRANSFER }

/**
 * Uma transação. REQ-TXN-001 · REQ-TXN-002 · REQ-TXN-003
 *
 * [amountCents] é sempre **o efeito líquido na conta [accountId]**:
 * `INCOME` positivo, `EXPENSE` e `TRANSFER` negativos.
 *
 * Transferência é **um** registro, não dois: [counterAccountId] guarda o
 * destino ([ADR-003](../../../../../../../../docs/decisoes.md)). Duas linhas
 * espelhadas exigiriam sincronia em toda edição, exclusão e desfazer de
 * importação — e meia transferência órfã é dinheiro inventado no saldo.
 */
data class Txn(
    val id: Long = 0,
    val accountId: Long,
    val type: TxnType,
    val amountCents: Long,
    val date: LocalDate,
    val counterAccountId: Long? = null,
    val categoryId: Long? = null,
    val description: String = "",
    val cleared: Boolean = true,
    val installmentGroupId: String? = null,
    val installmentIndex: Int? = null,
    val installmentTotal: Int? = null,
)
