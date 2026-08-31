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

/**
 * O que [txn] faz com o saldo de [contaId]. Os dois termos da fórmula acima,
 * para **uma** transação.
 *
 * Extraído de [balanceOf] porque o total do dia e o saldo corrente do extrato
 * (T-014) precisam exatamente desta regra. Reescrevê-la lá criaria uma terceira
 * fonte de verdade para a conta mais sensível do app, e a errada seria a que
 * ninguém testou.
 *
 * Não filtra por `cleared`: quem chama decide se previsto entra. [balanceOf]
 * exclui (REQ-TXN-006); o cabeçalho de dia da lista soma o que está visível.
 */
fun efeitoEm(txn: Txn, contaId: Long): Long {
    var efeito = 0L
    if (txn.accountId == contaId) efeito += txn.amountCents
    if (txn.counterAccountId == contaId) efeito -= txn.amountCents
    return efeito
}

/**
 * O que [txn] faz com o patrimônio somado de todas as contas.
 *
 * Transferência é **zero**: a perna de destino está dentro do conjunto, e o que
 * saiu de uma conta entrou na outra. Somar `amountCents` cru faria R$ 1.000
 * mudando de bolso parecer prejuízo de R$ 1.000 no cabeçalho do dia.
 *
 * Chave em `counterAccountId`, não em `type == TRANSFER`: é o destino dentro do
 * conjunto que zera o efeito, e é isso que o nome precisa dizer.
 */
fun efeitoGlobal(txn: Txn): Long = if (txn.counterAccountId != null) 0 else txn.amountCents

fun balanceOf(account: Account, txns: List<Txn>): Long =
    account.initialBalanceCents +
        txns.sumOf { if (it.cleared) efeitoEm(it, account.id) else 0 } // previsto não entra (REQ-TXN-006)

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
