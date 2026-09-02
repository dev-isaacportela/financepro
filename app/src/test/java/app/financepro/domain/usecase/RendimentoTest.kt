package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** REQ-INV-002 · REQ-INV-004 */
@Req("REQ-INV-002", "REQ-INV-004")
class RendimentoTest {

    private val conta = UMA_CONTA.copy(id = 1, name = "CDB", type = AccountType.INVESTMENT)
    private val corrente = UMA_CONTA.copy(id = 2, name = "Corrente")

    /** 1,1642% a.m., a mensal de 14,90% a.a. */
    private val mensal = 11_642

    private fun aporte(cents: Long, data: LocalDate) = Txn(
        accountId = corrente.id,
        type = TxnType.TRANSFER,
        amountCents = -cents,
        date = data,
        counterAccountId = conta.id,
    )

    private fun rendimento(cents: Long, data: LocalDate) = Txn(
        accountId = conta.id,
        type = TxnType.INCOME,
        amountCents = cents,
        date = data,
        categoryId = 11,
    )

    @Test
    fun `mil reais a um vírgula um seis quatro dois por cento rendem onze e sessenta e quatro`() {
        assertEquals(1164, rendimentoPrevisto(100_000, mensal))
    }

    @Test
    fun `saldo zerado rende zero`() {
        assertEquals(0, rendimentoPrevisto(0, mensal))
    }

    @Test
    fun `saldo negativo rende zero, nao um numero negativo`() {
        // Investimento no vermelho é conta errada. Lançar o "rendimento" dele
        // como receita negativa seria inventar uma despesa que ninguém teve.
        assertEquals(0, rendimentoPrevisto(-100_000, mensal))
    }

    @Test
    fun `taxa zerada rende zero`() {
        assertEquals(0, rendimentoPrevisto(100_000, 0))
    }

    @Test
    fun `arredonda para o centavo mais proximo, meio para cima`() {
        // 1 centavo a 1,5 ppm daria 0,0000015 — o que importa é a fronteira:
        // 100 centavos a 5000 ppm dão exatamente 0,5 centavo, e sobe.
        assertEquals(1, rendimentoPrevisto(100, 5_000))
        assertEquals(0, rendimentoPrevisto(100, 4_999))
    }

    @Test
    fun `aporte do mes nao vira rendimento`() {
        // O teste que prova a convenção do módulo: transferência entrando é
        // aporte, e só INCOME é rendimento. Confundir os dois faria a série
        // mostrar um "rendimento" do tamanho do aporte.
        val txns = listOf(aporte(500_000, LocalDate.of(2026, 8, 10)))

        val agosto = serieMensal(txns, conta, YearMonth.of(2026, 8), meses = 1).single()

        assertEquals(0, agosto.rendimentoCents)
        assertEquals(500_000, agosto.aporteCents)
        assertEquals(500_000, agosto.saldoFimCents)
    }

    @Test
    fun `resgate entra no aporte com sinal negativo`() {
        val txns = listOf(
            aporte(500_000, LocalDate.of(2026, 8, 5)),
            Txn(
                accountId = conta.id,
                type = TxnType.TRANSFER,
                amountCents = -200_000,
                date = LocalDate.of(2026, 8, 20),
                counterAccountId = corrente.id,
            ),
        )

        val agosto = serieMensal(txns, conta, YearMonth.of(2026, 8), meses = 1).single()

        assertEquals(300_000, agosto.aporteCents)
        assertEquals(300_000, agosto.saldoFimCents)
    }

    @Test
    fun `a serie separa rendimento, aporte e saldo mes a mes`() {
        val txns = listOf(
            aporte(1_000_000, LocalDate.of(2026, 6, 15)),
            rendimento(11_642, LocalDate.of(2026, 7, 31)),
            rendimento(11_777, LocalDate.of(2026, 8, 31)),
        )

        val serie = serieMensal(txns, conta, YearMonth.of(2026, 8), meses = 3)

        assertEquals(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)), serie.map { it.mes })
        assertEquals(listOf(0L, 11_642L, 11_777L), serie.map { it.rendimentoCents })
        assertEquals(listOf(1_000_000L, 0L, 0L), serie.map { it.aporteCents })
        assertEquals(listOf(1_000_000L, 1_011_642L, 1_023_419L), serie.map { it.saldoFimCents })
    }

    @Test
    fun `mes sem movimento carrega o saldo do mes anterior`() {
        val txns = listOf(aporte(1_000_000, LocalDate.of(2026, 6, 15)))

        val serie = serieMensal(txns, conta, YearMonth.of(2026, 8), meses = 3)

        assertEquals(listOf(1_000_000L, 1_000_000L, 1_000_000L), serie.map { it.saldoFimCents })
    }

    @Test
    fun `transacao de outra conta nao entra na serie`() {
        val txns = listOf(
            Txn(
                accountId = corrente.id,
                type = TxnType.INCOME,
                amountCents = 900_000,
                date = LocalDate.of(2026, 8, 5),
                categoryId = 9,
            ),
        )

        val agosto = serieMensal(txns, conta, YearMonth.of(2026, 8), meses = 1).single()

        assertEquals(0, agosto.rendimentoCents)
        assertEquals(0, agosto.saldoFimCents)
    }

    @Test
    fun `o previsto do mes parte do saldo do mes anterior`() {
        // A base é o dinheiro que passou o mês rendendo. Um aporte feito **em**
        // agosto não rende agosto inteiro, e contá-lo inflaria o previsto.
        val txns = listOf(
            aporte(1_000_000, LocalDate.of(2026, 7, 10)),
            aporte(9_000_000, LocalDate.of(2026, 8, 20)),
        )

        assertEquals(11_642, previstoDe(txns, conta, YearMonth.of(2026, 8), mensal))
    }

    @Test
    fun `previsto de mes sem saldo anterior e zero`() {
        val txns = listOf(aporte(1_000_000, LocalDate.of(2026, 8, 20)))

        assertEquals(0, previstoDe(txns, conta, YearMonth.of(2026, 8), mensal))
    }

    @Test
    fun `o previsto de um mes passado nao muda com aporte de depois`() {
        // O defeito que a base "saldo de hoje" produziria: o previsto de julho
        // subindo em setembro, sem nada ter acontecido em julho.
        val julho = YearMonth.of(2026, 7)
        val txns = listOf(aporte(1_000_000, LocalDate.of(2026, 6, 10)))

        val antes = previstoDe(txns, conta, julho, mensal)
        val depois = previstoDe(txns + aporte(50_000_000, LocalDate.of(2026, 9, 1)), conta, julho, mensal)

        assertEquals(antes, depois)
    }

    @Test
    fun `a serie somada e a soma das contas, mes a mes`() {
        // Art. 7 em espírito: o total do patrimônio investido não pode
        // discordar da soma das partes que a mesma tela lista.
        val outra = UMA_CONTA.copy(id = 3, name = "Tesouro", type = AccountType.INVESTMENT)
        val txns = listOf(
            aporte(1_000_000, LocalDate.of(2026, 8, 1)),
            Txn(
                accountId = corrente.id,
                type = TxnType.TRANSFER,
                amountCents = -400_000,
                date = LocalDate.of(2026, 8, 2),
                counterAccountId = outra.id,
            ),
            rendimento(11_642, LocalDate.of(2026, 8, 31)),
        )

        val somada = serieSomada(txns, listOf(conta, outra), YearMonth.of(2026, 8), meses = 1).single()

        assertEquals(1_411_642, somada.saldoFimCents)
        assertEquals(1_400_000, somada.aporteCents)
        assertEquals(11_642, somada.rendimentoCents)
        assertEquals(
            somada.saldoFimCents,
            serieMensal(txns, conta, YearMonth.of(2026, 8), 1).single().saldoFimCents +
                serieMensal(txns, outra, YearMonth.of(2026, 8), 1).single().saldoFimCents,
        )
    }

    @Test
    fun `sem conta nenhuma, a serie soma zero e nao estoura`() {
        val serie = serieSomada(emptyList(), emptyList(), YearMonth.of(2026, 8), meses = 3)

        assertEquals(3, serie.size)
        assertEquals(listOf(0L, 0L, 0L), serie.map { it.saldoFimCents })
    }

    @Test
    fun `rendimento ja lancado no mes e reconhecido`() {
        val txns = listOf(rendimento(11_642, LocalDate.of(2026, 8, 31)))

        assertEquals(11_642, rendimentoDe(txns, conta, YearMonth.of(2026, 8)))
        assertEquals(0, rendimentoDe(txns, conta, YearMonth.of(2026, 9)))
    }

    @Test
    fun `previsto nao entra, como no saldo`() {
        // REQ-TXN-006: o rendimento que ainda não caiu não é patrimônio.
        val txns = listOf(rendimento(11_642, LocalDate.of(2026, 8, 31)).copy(cleared = false))

        val agosto = serieMensal(txns, conta, YearMonth.of(2026, 8), meses = 1).single()

        assertEquals(0, agosto.rendimentoCents)
        assertEquals(0, agosto.saldoFimCents)
    }
}
