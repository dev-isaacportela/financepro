package app.financepro.domain.usecase

import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn

/**
 * Saldo de conta.
 *
 * REQ-ACC-003 · REQ-ACC-004 · REQ-ACC-007 · REQ-TXN-006 · REQ-CARD-009 ·
 * Art. 7 · [ADR-003](../../../../../../../../docs/decisoes.md)
 *
 * ```
 * saldo(X) = initialBalanceCents(X)
 *          + SUM(amountCents) WHERE accountId        = X  AND cleared
 *          - SUM(amountCents) WHERE counterAccountId = X  AND cleared
 * ```
 *
 * O segundo termo é o que faz a transferência funcionar com **uma** linha: a
 * saída de −100000 na origem, invertida, vira +100000 no destino. É também o
 * que faz o pagamento de fatura de cartão funcionar sem nenhum código especial
 * de cartão — ele é só uma transferência da conta corrente para o cartão.
 */
fun balanceOf(account: Account, txns: List<Txn>): Long {
    var saldo = account.initialBalanceCents
    for (t in txns) {
        if (!t.cleared) continue          // previsto não entra no saldo (REQ-TXN-006)
        if (t.accountId == account.id) saldo += t.amountCents
        if (t.counterAccountId == account.id) saldo -= t.amountCents
    }
    return saldo
}

/**
 * Saldo total do dashboard. REQ-ACC-007.
 *
 * Soma apenas contas não arquivadas **que não sejam cartão**. Misturar a dívida
 * do cartão no saldo faz o usuário acreditar que tem mais dinheiro do que tem —
 * é o erro clássico da categoria, e é requisito não cometê-lo. A dívida aparece
 * como número separado, via [cardDebt].
 */
fun totalBalance(accounts: List<Account>, txns: List<Txn>): Long =
    accounts
        .filter { !it.archived && it.type != AccountType.CREDIT_CARD }
        .sumOf { balanceOf(it, txns) }

/**
 * Dívida total dos cartões, como número **positivo** para exibição.
 *
 * O saldo de um cartão é negativo quando se deve (REQ-CARD-009, mesma fórmula
 * sem exceção). Aqui o sinal é invertido só na borda de apresentação.
 */
fun cardDebt(accounts: List<Account>, txns: List<Txn>): Long =
    -accounts
        .filter { !it.archived && it.type == AccountType.CREDIT_CARD }
        .sumOf { balanceOf(it, txns) }
