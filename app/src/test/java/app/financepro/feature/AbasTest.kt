package app.financepro.feature

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * REQ-UI-001 — o deslize entre as quatro abas.
 *
 * O que se prova aqui é o **sinal**. Ele é a única coisa deste gesto que passa
 * por uma revisão inteira estando trocada: um app que anda para o lado errado
 * continua andando, e quem lê o diff vê `indice - sign` e concorda. Uma tela de
 * teste do Compose não acrescentaria nada — o resto é `draggable`, que é da
 * biblioteca.
 *
 * Início(0) · Transações(1) · Orçamento(2) · Mais(3).
 */
@Req("REQ-UI-001")
class AbasTest {

    private fun deslize(indice: Int, arrasto: Float, velocidade: Float = 0f) =
        abaDoDeslize(indice, arrasto, velocidade, PERCURSO, ARREMESSO)

    @Test
    fun `dedo para a esquerda vai para a aba da direita`() {
        assertEquals(1, deslize(indice = 0, arrasto = -200f))
        assertEquals(3, deslize(indice = 2, arrasto = -200f))
    }

    @Test
    fun `dedo para a direita vai para a aba da esquerda`() {
        assertEquals(0, deslize(indice = 1, arrasto = 200f))
        assertEquals(2, deslize(indice = 3, arrasto = 200f))
    }

    @Test
    fun `percurso curto e devagar nao troca de aba`() {
        assertNull(deslize(indice = 1, arrasto = -PERCURSO + 1f))
        assertNull(deslize(indice = 1, arrasto = PERCURSO - 1f))
    }

    @Test
    fun `peteleco curto troca de aba, e manda no sentido`() {
        // Percurso abaixo do limiar, mas velocidade acima: é como se troca de
        // aba com pressa.
        assertEquals(2, deslize(indice = 1, arrasto = -10f, velocidade = -ARREMESSO - 1f))
        // E quando os dois discordam, a velocidade é a última palavra do dedo:
        // arrastou para a esquerda e devolveu num peteleco para a direita.
        assertEquals(0, deslize(indice = 1, arrasto = -200f, velocidade = ARREMESSO + 1f))
    }

    @Test
    fun `as pontas nao dao a volta`() {
        assertNull(deslize(indice = 0, arrasto = 200f))
        assertNull(deslize(indice = 3, arrasto = -200f))
    }

    @Test
    fun `sub-tela nao desliza`() {
        // -1 é o que `indexOfFirst` devolve em Contas, Relatórios e companhia.
        assertNull(deslize(indice = -1, arrasto = -200f))
    }

    private companion object {
        const val PERCURSO = 150f
        const val ARREMESSO = 330f
    }
}
