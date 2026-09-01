package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * REQ-CARD-002 — a faixa de dia, com a mensagem exata da spec.
 *
 * A tela oferece só 1 a 28 em chips, então nenhum destes valores chega por ali.
 * Chegam pelo resto: importação (F2), restauração de backup (T-035) e a próxima
 * tela que escrever uma conta. É por isso que a regra é do domínio e o teste
 * ataca a função, não o formulário.
 */
@Req("REQ-CARD-001", "REQ-CARD-002", "REQ-ACC-002")
class ValidateAccountTest {

    private val cartao = UMA_CONTA.copy(
        name = "Nubank",
        type = AccountType.CREDIT_CARD,
        creditLimitCents = 5_000_00,
        closingDay = 10,
        dueDay = 17,
    )

    @Test
    fun `cartao com limite e dias validos passa`() {
        assertNull(validateAccount(cartao))
    }

    @Test
    fun `recusa dia 0, 29, 31 e negativo, nos dois campos`() {
        val fora = listOf(0, 29, 31, -1)
        for (dia in fora) {
            assertEquals("fechamento $dia", "Use um dia entre 1 e 28", validateAccount(cartao.copy(closingDay = dia)))
            assertEquals("vencimento $dia", "Use um dia entre 1 e 28", validateAccount(cartao.copy(dueDay = dia)))
        }
    }

    @Test
    fun `as bordas 1 e 28 passam`() {
        // A faixa é fechada nos dois lados. Um `until` no lugar do `..` recusaria
        // o dia 28, que é o mais comum de todos em fatura.
        assertNull(validateAccount(cartao.copy(closingDay = 1, dueDay = 28)))
        assertNull(validateAccount(cartao.copy(closingDay = 28, dueDay = 1)))
    }

    @Test
    fun `cartao sem limite, sem fechamento ou sem vencimento e recusado`() {
        assertEquals("Informe o limite do cartão", validateAccount(cartao.copy(creditLimitCents = null)))
        assertEquals("Informe o dia de fechamento", validateAccount(cartao.copy(closingDay = null)))
        assertEquals("Informe o dia de vencimento", validateAccount(cartao.copy(dueDay = null)))
    }

    @Test
    fun `conta que nao e cartao nao herda nenhuma das tres exigencias`() {
        // REQ-ACC-002 — sem esta linha, criar uma conta corrente pediria limite
        // de cartão, que é o erro que a regra dentro do ViewModel já evitava e
        // que a mudança de casa não pode perder.
        val corrente = UMA_CONTA.copy(name = "Corrente", type = AccountType.CHECKING)
        assertNull(validateAccount(corrente))
    }

    @Test
    fun `nome em branco e recusado antes de qualquer regra de cartao`() {
        assertEquals("Dê um nome à conta", validateAccount(cartao.copy(name = "   ")))
    }
}
