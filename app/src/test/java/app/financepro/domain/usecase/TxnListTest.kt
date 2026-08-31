package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-TXN-011 · REQ-TXN-012 · REQ-ACC-005
 *
 * A lista é regra, e é aqui que ela é provada — sem emulador, em
 * milissegundos. Os três casos que dão errado sozinhos estão todos cobertos:
 * transferência no total do dia, saldo corrente que ignora o passado, e busca
 * por valor com o sinal trocado.
 */
@Req("REQ-TXN-011", "REQ-TXN-012", "REQ-ACC-005")
class TxnListTest {

    private val corrente = UMA_CONTA.copy(id = 1, name = "Corrente", initialBalanceCents = 100_000)
    private val poupanca = UMA_CONTA.copy(id = 2, name = "Poupança")

    private fun dia(d: Int) = LocalDate.of(2026, 3, d)

    private fun despesa(
        id: Long,
        d: Int,
        cents: Long,
        conta: Long = 1,
        categoria: Long? = 10,
    ) = Txn(
        id = id,
        accountId = conta,
        type = TxnType.EXPENSE,
        amountCents = -cents,
        date = dia(d),
        categoryId = categoria,
    )

    private fun receita(id: Long, d: Int, cents: Long, conta: Long = 1) =
        Txn(id = id, accountId = conta, type = TxnType.INCOME, amountCents = cents, date = dia(d), categoryId = 20)

    private fun transferencia(id: Long, d: Int, cents: Long, de: Long = 1, para: Long = 2) = Txn(
        id = id,
        accountId = de,
        counterAccountId = para,
        type = TxnType.TRANSFER,
        amountCents = -cents,
        date = dia(d),
    )

    // ---------- agrupamento (REQ-TXN-011) ----------

    @Test
    fun `dias em ordem decrescente, e a entrada desordenada nao muda o resultado`() {
        // Embaralhado de propósito: uma função pura que só acerta quando quem
        // chama já ordenou não é uma função pura, e o teste estaria provando a
        // ordem do `ORDER BY` do DAO em vez da regra.
        val dias = agruparPorDia(listOf(despesa(1, 10, 100), despesa(2, 20, 100), despesa(3, 15, 100)))

        assertEquals(listOf(dia(20), dia(15), dia(10)), dias.map { it.data })
    }

    @Test
    fun `dentro do dia, o mais recente primeiro`() {
        val dias = agruparPorDia(listOf(despesa(1, 10, 100), despesa(3, 10, 100), despesa(2, 10, 100)))

        assertEquals(listOf(3L, 2L, 1L), dias.single().itens.map { it.id })
    }

    @Test
    fun `total do dia soma receita e despesa`() {
        val dias = agruparPorDia(listOf(despesa(1, 10, 1_850), receita(2, 10, 5_000)))

        assertEquals(5_000L - 1_850L, dias.single().totalCents)
    }

    /** Art. 7 · ADR-003 — o caso que justifica `efeitoGlobal` existir. */
    @Test
    fun `transferencia entre contas proprias nao vira prejuizo no total do dia`() {
        val movimento = listOf(transferencia(1, 10, cents = 100_000))

        // Na visão geral, R$ 1.000 mudando de bolso não é gasto nem ganho.
        assertEquals(0L, agruparPorDia(movimento).single().totalCents)
        // No extrato de cada conta, é saída de um lado e entrada do outro.
        assertEquals(-100_000L, agruparPorDia(movimento, contaId = 1).single().totalCents)
        assertEquals(100_000L, agruparPorDia(movimento, contaId = 2).single().totalCents)
    }

    /**
     * Amarra a regra global à regra por conta, em vez de manter duas.
     *
     * Se alguém trocar `efeitoGlobal` por `if (type == TRANSFER) 0`, isto
     * continua passando — mas se trocar por `amountCents` cru, quebra na hora.
     */
    @Test
    fun `o efeito global e a soma dos efeitos em cada conta`() {
        val movimento = listOf(
            despesa(1, 10, 1_850),
            receita(2, 10, 5_000),
            transferencia(3, 10, cents = 100_000),
        )

        val global = movimento.sumOf { efeitoGlobal(it) }
        val porConta = listOf(corrente, poupanca).sumOf { c -> movimento.sumOf { efeitoEm(it, c.id) } }

        assertEquals(porConta, global)
    }

    // ---------- extrato (REQ-ACC-005) ----------

    @Test
    fun `a ultima linha do extrato e o saldo da conta`() {
        // O invariante que impede o extrato de discordar da tela de Contas —
        // que era a razão de a T-015 não duplicar a lista aqui.
        val movimento = listOf(despesa(1, 10, 1_850), receita(2, 12, 5_000), transferencia(3, 14, 30_000))

        val linhas = extrato(corrente, movimento)

        assertEquals(balanceOf(corrente, movimento), linhas.first().saldoCents)
        assertEquals(3, linhas.size)
    }

    @Test
    fun `o extrato acumula do mais antigo para o mais recente e devolve invertido`() {
        val linhas = extrato(corrente, listOf(despesa(1, 10, 1_000), despesa(2, 12, 2_000)))

        // Devolve decrescente: a de cima é a mais nova, com o saldo mais novo.
        assertEquals(listOf(dia(12), dia(10)), linhas.map { it.txn.date })
        assertEquals(listOf(97_000L, 99_000L), linhas.map { it.saldoCents })
    }

    @Test
    fun `previsto nao mexe no saldo corrente`() {
        // Mesma regra de `balanceOf` (REQ-TXN-006): a linha aparece, o saldo repete.
        val linhas = extrato(corrente, listOf(despesa(1, 10, 1_000), despesa(2, 12, 9_999).copy(cleared = false)))

        assertEquals(listOf(99_000L, 99_000L), linhas.map { it.saldoCents })
    }

    @Test
    fun `a perna de destino da transferencia sobe o saldo de quem recebe`() {
        val linhas = extrato(poupanca, listOf(transferencia(1, 10, cents = 30_000)))

        assertEquals(30_000L, linhas.single().saldoCents)
    }

    // ---------- filtros e busca (REQ-TXN-012) ----------

    @Test
    fun `filtro de conta pega tambem a perna de destino`() {
        // ADR-003: sem isto a transferência sumiria do extrato de quem recebeu,
        // e o dinheiro pareceria entrar do nada.
        val movimento = listOf(despesa(1, 10, 100), transferencia(2, 10, cents = 30_000))

        assertEquals(listOf(2L), filtrar(movimento, Filtro(contaId = 2)).map { it.id })
    }

    @Test
    fun `filtros de categoria e tipo`() {
        val movimento = listOf(despesa(1, 10, 100), receita(2, 10, 100), despesa(3, 10, 100, categoria = 99))

        assertEquals(listOf(1L, 3L), filtrar(movimento, Filtro(tipo = TxnType.EXPENSE)).map { it.id })
        assertEquals(listOf(3L), filtrar(movimento, Filtro(categoriaId = 99)).map { it.id })
    }

    @Test
    fun `os filtros se combinam com E, nunca com OU`() {
        val movimento = listOf(
            despesa(1, 10, 100, conta = 1, categoria = 10),
            despesa(2, 10, 100, conta = 2, categoria = 10),
            despesa(3, 10, 100, conta = 1, categoria = 99),
        )

        val so = filtrar(movimento, Filtro(contaId = 1, categoriaId = 10, tipo = TxnType.EXPENSE))

        assertEquals(listOf(1L), so.map { it.id })
    }

    @Test
    fun `busca por trecho da descricao ignora a caixa`() {
        val movimento = listOf(despesa(1, 10, 100).copy(description = "Padaria do Zé"), despesa(2, 10, 100))

        assertEquals(listOf(1L), filtrar(movimento, Filtro(busca = "padaria")).map { it.id })
        assertEquals(listOf(1L), filtrar(movimento, Filtro(busca = "DO ZÉ")).map { it.id })
    }

    /** O sinal é convenção do banco (REQ-TXN-002); ninguém digita `−18,50`. */
    @Test
    fun `busca por valor exato acha a despesa sem exigir o sinal`() {
        val movimento = listOf(despesa(1, 10, 1_850), despesa(2, 10, 1_851))

        assertEquals(listOf(1L), filtrar(movimento, Filtro(busca = "18,50")).map { it.id })
        // Aceita o que o próprio app imprime, colado de volta no campo.
        assertEquals(listOf(1L), filtrar(movimento, Filtro(busca = "R$ 18,50")).map { it.id })
    }

    @Test
    fun `busca por valor entende os dois formatos de milhar`() {
        val movimento = listOf(receita(1, 10, 123_456))

        assertEquals(listOf(1L), filtrar(movimento, Filtro(busca = "1.234,56")).map { it.id })
        assertEquals(listOf(1L), filtrar(movimento, Filtro(busca = "1,234.56")).map { it.id })
    }

    @Test
    fun `busca sem casamento devolve vazio, e busca vazia devolve tudo`() {
        val movimento = listOf(despesa(1, 10, 100).copy(description = "Padaria"), despesa(2, 10, 200))

        assertEquals(emptyList<Long>(), filtrar(movimento, Filtro(busca = "supermercado")).map { it.id })
        assertEquals(2, filtrar(movimento, Filtro(busca = "   ")).size)
        assertEquals(2, filtrar(movimento, Filtro()).size)
    }

    @Test
    fun `filtro vazio nao se diz ativo`() {
        assertTrue(!Filtro().ativo)
        assertTrue(Filtro(busca = "x").ativo)
        assertTrue(Filtro(contaId = 1).ativo)
    }
}
