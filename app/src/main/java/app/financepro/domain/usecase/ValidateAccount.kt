package app.financepro.domain.usecase

import app.financepro.domain.model.Account

/**
 * Validação de conta. REQ-ACC-002 · REQ-CARD-001 · REQ-CARD-002 · Art. 9
 *
 * A regra nasceu privada no `AccountsViewModel` (T-015), que já escrevia que a
 * faixa de dia era da T-022 e que duplicar metade dela no domínio criaria a
 * segunda fonte de verdade a reconciliar depois. Esta é a task que reconcilia:
 * a regra mora aqui, e a tela chama.
 *
 * Devolve **uma** mensagem, e não a lista de [validateTxn]: o formulário de
 * conta tem uma linha de erro, e uma lista obrigaria a mudar a tela para exibir
 * o que ela não tem onde pôr.
 */

/** A mensagem do primeiro problema, ou `null` quando a conta está de pé. */
fun validateAccount(conta: Account): String? {
    val fechamento = conta.closingDay
    val vencimento = conta.dueDay
    return when {
        conta.name.isBlank() -> "Dê um nome à conta"
        // REQ-ACC-002 — as três exigências valem só para cartão. Uma conta
        // corrente com dia de fechamento seria campo sem sentido, e a tela nem
        // os oferece.
        !conta.isCard -> null
        conta.creditLimitCents == null -> "Informe o limite do cartão"
        fechamento == null -> "Informe o dia de fechamento"
        vencimento == null -> "Informe o dia de vencimento"
        // REQ-CARD-002 fixa uma mensagem só para os dois campos. Dizer qual dos
        // dois está fora seria outra mensagem, e a spec soletra esta.
        fechamento !in CARD_DAY_RANGE || vencimento !in CARD_DAY_RANGE ->
            "Use um dia entre 1 e 28"
        else -> null
    }
}
