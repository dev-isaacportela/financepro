package app.financepro.domain.usecase

import app.financepro.domain.model.Account
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Competência e vencimento de fatura de cartão.
 *
 * REQ-CARD-003 · REQ-CARD-004 · [ADR-004](../../../../../../../../docs/decisoes.md)
 *
 * Não existe tabela `invoice`: a fatura é derivada destas duas funções. Fatura
 * materializada precisaria ser criada, fechada, reaberta quando o usuário edita
 * a data de uma compra antiga, e recalculada a cada importação — estado
 * derivado armazenado é estado que sai de sincronia.
 *
 * O agrupamento acontece em Kotlin e não em SQL porque SQLite não tem
 * aritmética de data decente, e replicar a regra num `@Query` criaria a segunda
 * fonte de verdade que o Art. 9 proíbe.
 */

/** Faixa válida para fechamento e vencimento. Ver [requireCardDay]. */
val CARD_DAY_RANGE = 1..28

/**
 * Bancos brasileiros não fecham fatura nos dias 29 a 31, então restringir a
 * entrada a 1–28 elimina a classe inteira de bugs de "dia 31 em fevereiro"
 * **sem uma linha de tratamento** — e sem perder caso real.
 *
 * É o oposto da recorrência (REQ-REC-006), onde o clamp é obrigatório porque
 * conta que vence dia 30 é comum.
 */
private fun requireCardDay(day: Int, nome: String) =
    require(day in CARD_DAY_RANGE) {
        "$nome deve estar entre 1 e 28, recebido $day (REQ-CARD-002)"
    }

/**
 * Mês da fatura em que a compra cai.
 *
 * Compra **até** o dia do fechamento, inclusive, entra na fatura que fecha
 * naquele mesmo mês; depois do fechamento, cai na seguinte.
 *
 * | `closingDay` | compra       | fatura    |
 * |--------------|--------------|-----------|
 * | 10           | 2026-03-09   | 2026-03   |
 * | 10           | 2026-03-10   | 2026-03   |
 * | 10           | 2026-03-11   | 2026-04   |
 * | 10           | 2026-12-15   | 2027-01   |
 */
fun invoiceMonthFor(purchaseDate: LocalDate, closingDay: Int): YearMonth {
    requireCardDay(closingDay, "closingDay")
    val month = YearMonth.from(purchaseDate)
    return if (purchaseDate.dayOfMonth <= closingDay) month else month.plusMonths(1)
}

/**
 * Vencimento da fatura que fecha em [invoiceMonth].
 *
 * Se o vencimento cai depois do fechamento, vence no próprio mês; senão, no
 * seguinte. `dueDay == closingDay` conta como "não depois", e portanto vence no
 * mês seguinte — um cartão não fecha e vence no mesmo dia.
 *
 * | `closingDay` | `dueDay` | fatura   | vencimento |
 * |--------------|----------|----------|------------|
 * | 10           | 20       | 2026-03  | 2026-03-20 |
 * | 20           | 10       | 2026-03  | 2026-04-10 |
 * | 10           | 10       | 2026-03  | 2026-04-10 |
 * | 25           | 5        | 2026-12  | 2027-01-05 |
 */
fun dueDateFor(invoiceMonth: YearMonth, closingDay: Int, dueDay: Int): LocalDate {
    requireCardDay(closingDay, "closingDay")
    requireCardDay(dueDay, "dueDay")
    val month = if (dueDay > closingDay) invoiceMonth else invoiceMonth.plusMonths(1)
    return month.atDay(dueDay)
}

/**
 * Data em que a fatura de [invoiceMonth] fecha.
 *
 * Sem clamp e sem surpresa: `CARD_DAY_RANGE` garante que o dia existe em todo
 * mês, inclusive fevereiro de ano não bissexto.
 */
fun closingDateFor(invoiceMonth: YearMonth, closingDay: Int): LocalDate {
    requireCardDay(closingDay, "closingDay")
    return invoiceMonth.atDay(closingDay)
}

/** REQ-CARD-007 — os três estados, derivados. Nenhum deles mora em coluna. */
enum class InvoiceStatus { ABERTA, FECHADA, PAGA }

/**
 * Uma fatura. REQ-CARD-005 · REQ-CARD-007 · ADR-004
 *
 * Objeto de resultado, não de banco: nasce de [invoiceFor] a cada leitura e
 * morre com a tela. Nada aqui é persistido — nem o total, nem o status, nem a
 * lista. Persistir qualquer um deles obrigaria a recalcular a fatura em toda
 * importação, em toda edição de data de compra antiga, e a errada seria sempre
 * a que ninguém olhou.
 */
data class Invoice(
    val month: YearMonth,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val items: List<Txn>,
    /** Quanto já entrou no cartão desde o fechamento desta fatura. Positivo. */
    val paidCents: Long,
    val status: InvoiceStatus,
) {
    /**
     * O total da fatura, **positivo**: é quanto se deve.
     *
     * As compras entram negativas (REQ-TXN-002), então o sinal é invertido aqui,
     * na borda de leitura — mesma escolha de `cardDebt`. Um estorno no cartão é
     * uma receita, positiva, e abate o total pela mesma soma, sem caso especial.
     */
    val totalCents: Long get() = -items.sumOf { it.amountCents }

    /**
     * O que falta pagar. REQ-CARD-006
     *
     * Nunca negativo: pagar a mais deixa crédito no cartão (o saldo cuida
     * disso), e um "falta −R$ 100" seria a tela pedindo para o usuário
     * interpretar um sinal em vez de ler um número.
     */
    val restanteCents: Long get() = (totalCents - paidCents).coerceAtLeast(0)
}

/**
 * Compõe a fatura de [month]. REQ-CARD-005
 *
 * A regra de escopo é a do ADR-004, e a exclusão de `TRANSFER` é a parte que
 * mais custa quando falta: pagamento de fatura é uma transferência **para** o
 * cartão, e contá-lo como item faria o pagamento aumentar a conta que ele
 * quita.
 *
 * [hoje] é parâmetro, e não `LocalDate.now()`, porque o status depende dele —
 * ler o relógio aqui dentro tornaria o teste dependente do dia em que roda.
 */
fun invoiceFor(card: Account, txns: List<Txn>, month: YearMonth, hoje: LocalDate): Invoice {
    val closingDay = requireNotNull(card.closingDay) { "cartão sem closingDay (REQ-CARD-001)" }
    val dueDay = requireNotNull(card.dueDay) { "cartão sem dueDay (REQ-CARD-001)" }

    val items = txns.filter {
        it.accountId == card.id &&
            it.type != TxnType.TRANSFER &&
            invoiceMonthFor(it.date, closingDay) == month
    }
    val pago = txns
        .filter { it.type == TxnType.TRANSFER && it.counterAccountId == card.id }
        .filter { paymentInvoiceMonthFor(it.date, closingDay) == month }
        .sumOf { -it.amountCents }

    val fechamento = closingDateFor(month, closingDay)
    val total = -items.sumOf { it.amountCents }

    return Invoice(
        month = month,
        closingDate = fechamento,
        dueDate = dueDateFor(month, closingDay, dueDay),
        items = items,
        paidCents = pago,
        // REQ-CARD-007, na ordem da tabela da spec. Fatura vazia depois do
        // fechamento cai em `PAGA` por `0 >= 0`, que é o que ela é: não há o que
        // dever. Um quarto estado para isso seria vocabulário sem consequência.
        status = when {
            !hoje.isAfter(fechamento) -> InvoiceStatus.ABERTA
            pago >= total -> InvoiceStatus.PAGA
            else -> InvoiceStatus.FECHADA
        },
    )
}

/**
 * A fatura que um pagamento quita: a última que fechou antes dele.
 *
 * O espelho de [invoiceMonthFor], e existe pela mesma razão que ela. "Pagamentos
 * desde o fechamento" sem limite superior faria toda fatura antiga virar `PAGA`
 * com o tempo — o pagamento de março abateria a fatura de janeiro, de fevereiro
 * e de março ao mesmo tempo.
 *
 * O dia do fechamento conta para a fatura que fecha nele, dos dois lados: uma
 * compra no dia 10 entra na fatura de março, e um pagamento no dia 10 abate a
 * mesma.
 */
private fun paymentInvoiceMonthFor(paymentDate: LocalDate, closingDay: Int): YearMonth {
    val month = YearMonth.from(paymentDate)
    return if (paymentDate.dayOfMonth >= closingDay) month else month.minusMonths(1)
}

/**
 * Limite disponível. REQ-CARD-008
 *
 * `limite + saldo`, e não `limite - total da fatura do mês`: a dívida do cartão
 * é tudo que está lançado, inclusive as parcelas de dezembro compradas hoje. O
 * saldo sai de [balanceOf] sem exceção de cartão (REQ-CARD-009), então o
 * pagamento de fatura devolve limite pelo mesmo termo que já move o saldo, sem
 * uma linha de código de cartão.
 *
 * Some porque o saldo do cartão é **negativo** quando se deve.
 */
fun availableLimitFor(card: Account, txns: List<Txn>): Long {
    val limite = requireNotNull(card.creditLimitCents) { "cartão sem creditLimitCents (REQ-CARD-001)" }
    return limite + balanceOf(card, txns)
}

/**
 * A transferência que quita [invoice]. REQ-CARD-006 · ADR-003
 *
 * Uma linha só, da conta de pagamento **para** o cartão: é a mesma
 * transferência de qualquer outra, e é por isso que o pagamento zera a dívida
 * sem uma linha de código de cartão em `balanceOf`. Um tipo `PAYMENT` próprio
 * exigiria tratamento especial em saldo, relatório, filtro e importação — para
 * dizer o que `TRANSFER` já diz.
 *
 * Nasce na data de **vencimento**, não hoje: é a data que o extrato do banco
 * vai mostrar, e quem paga adiantado corrige na folha.
 *
 * [amountCents] é positivo e separado porque REQ-CARD-006 exige valor editável —
 * pagamento parcial é caso comum, não exceção. O padrão é o que **falta**, e não
 * o total: com o total, pagar R$ 100 de R$ 300 e voltar para quitar ofereceria
 * R$ 300 de novo, e dois toques pagariam R$ 400.
 */
fun cardPaymentFor(card: Account, invoice: Invoice, amountCents: Long = invoice.restanteCents): Txn {
    val origem = requireNotNull(card.paymentAccountId) {
        "cartão sem paymentAccountId (REQ-CARD-001)"
    }
    return Txn(
        accountId = origem,
        type = TxnType.TRANSFER,
        // Sai da conta de pagamento, então é negativa nela; o segundo termo da
        // fórmula do ADR-003 é quem a faz chegar positiva no cartão.
        amountCents = -amountCents,
        date = invoice.dueDate,
        counterAccountId = card.id,
    )
}
