package app.financepro.domain.model

import java.time.LocalDate

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

/** REQ-ACC-001 · REQ-ACC-002 · REQ-CARD-001 */
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val initialBalanceCents: Long = 0,
    val archived: Boolean = false,
    // Só CREDIT_CARD:
    val creditLimitCents: Long? = null,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
    val paymentAccountId: Long? = null,
) {
    val isCard: Boolean get() = type == AccountType.CREDIT_CARD
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
