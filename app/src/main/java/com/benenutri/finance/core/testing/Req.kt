package com.benenutri.finance.core.testing

/**
 * Liga um teste ao requisito que ele cobre em `docs/spec.md`.
 *
 * É o elo que `tools/trace.py` percorre para provar que um requisito foi de
 * fato implementado (constitution.md Art. 4). Sem a anotação, a rastreabilidade
 * vira promessa em vez de verificação.
 *
 * `SOURCE` de propósito: serve à ferramenta e a quem lê o teste, não ao
 * runtime. Não entra no APK.
 *
 * Anotar a classe basta. Anotar o método individual é para quando uma classe
 * cobre mais de um requisito e a leitura fica ambígua:
 *
 * ```
 * @Req("REQ-CARD-003")
 * class InvoiceMonthTest {
 *
 *     @Test fun `compra ate o fechamento entra na fatura do mes`() { ... }
 *
 *     @Req("REQ-CARD-004")
 *     @Test fun `vencimento cai no mes seguinte quando dueDay < closingDay`() { ... }
 * }
 * ```
 *
 * Um id que não exista em `spec.md` é **erro**, não aviso: significa que
 * alguém renomeou ou removeu um requisito e deixou o teste órfão.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Req(vararg val ids: String)
