package app.financepro.domain.usecase

import app.financepro.domain.model.Account

/**
 * Validação de conta. REQ-ACC-002 · REQ-CARD-001 · REQ-CARD-002 · REQ-INV-001 · Art. 9
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

/**
 * A taxa que o formulário aceita, em pontos-base: de 0,01% a 1.000% ao ano.
 *
 * O teto não é preciosismo. `taxaBp` é digitada, e um zero a mais numa taxa de
 * CDI produz um rendimento previsto que a pessoa acredita — o número não vem de
 * lugar nenhum que a desminta.
 */
val TAXA_BP_RANGE = 1..100_000

/**
 * A mensagem do primeiro problema, ou `null` quando a conta está de pé.
 *
 * Cartão e investimento são os dois tipos com campos próprios, e cada um tem a
 * sua função: um `when` só, com os dois conjuntos de guardas em sequência,
 * exigiria que cada linha de cartão repetisse "e não é investimento".
 */
fun validateAccount(conta: Account): String? = when {
    conta.name.isBlank() -> "Dê um nome à conta"
    // REQ-ACC-002 — as exigências extras valem só para os tipos que as têm.
    // Uma conta corrente com dia de fechamento seria campo sem sentido, e a
    // tela nem os oferece.
    conta.isCard -> erroDeCartao(conta)
    conta.isInvestimento -> erroDeInvestimento(conta)
    else -> null
}

private fun erroDeCartao(conta: Account): String? {
    val fechamento = conta.closingDay
    val vencimento = conta.dueDay
    return when {
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

/**
 * REQ-INV-001 — investimento sem taxa não tem rendimento a acompanhar.
 *
 * Vale para a conta de investimento que já existia antes deste módulo: ela abre
 * com os dois campos vazios, e o formulário pede que sejam preenchidos na
 * primeira vez que for salva. É a única forma de o cálculo existir para ela.
 */
private fun erroDeInvestimento(conta: Account): String? {
    val taxa = conta.taxaBp
    return when {
        conta.indexador == null -> "Escolha o indexador"
        taxa == null -> "Informe a taxa"
        taxa !in TAXA_BP_RANGE -> "Use uma taxa entre 0,01% e 1.000%"
        else -> null
    }
}
