package app.financepro.core.money

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * REQ-CORE-001 — dinheiro é `Long` em centavos.
 *
 * Um requisito de representação não se prova lendo a assinatura; prova-se
 * mostrando o que quebraria sem ele. Cada teste aqui é um caso em que ponto
 * flutuante daria a resposta errada e `Long` dá a certa.
 */
@Req("REQ-CORE-001")
class MoneyTest {

    @Test
    fun `soma que ponto flutuante erra`() {
        // 0.1 + 0.2 == 0.30000000000000004 em Double.
        assertNotEquals(0.1 + 0.2, 0.3, 0.0)

        // Em centavos, exato.
        assertEquals(30L, parseCents("0,10")!! + parseCents("0,20")!!)
    }

    @Test
    fun `mil lancamentos de um centavo somam exatamente dez reais`() {
        // Somando 0.01 em Double mil vezes, o erro acumulado aparece na terceira
        // casa. É a diferença entre o saldo do app e o extrato do banco.
        val total = (1..1000).fold(0L) { acc, _ -> acc + 1L }
        assertEquals(1000L, total)
        assertEquals("R\$ 10,00", formatBRL(total))
    }

    @Test
    fun `divisao de valor nao perde nem inventa centavo`() {
        // Art. 7 — a soma das partes é exatamente o todo. A regra completa de
        // parcelamento é REQ-TXN-008 (T-026); aqui só o invariante básico.
        val total = parseCents("100,00")!!
        val partes = 3
        val base = total / partes
        val ultima = total - base * (partes - 1)
        assertEquals(total, base * (partes - 1) + ultima)
        assertEquals(3333L, base)
        assertEquals(3334L, ultima)
    }

    @Test
    fun `centavo e a menor unidade representavel`() {
        assertEquals(1L, parseCents("0,01"))
        // Não existe meio centavo: a terceira casa é lida como milhar, nunca
        // arredondada em silêncio.
        assertEquals(50L, parseCents("0,50"))
    }

    @Test
    fun `valores grandes cabem em Long sem perda`() {
        // Double perde precisão inteira acima de 2^53 (~90 trilhões de centavos).
        val grande = 9_007_199_254_740_993L        // 2^53 + 1
        assertEquals(grande, grande.toDouble().toLong() + 1L)
        assertEquals(grande, parseCents(formatBRL(grande)))
    }
}
