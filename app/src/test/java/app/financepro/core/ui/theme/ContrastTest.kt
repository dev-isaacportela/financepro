package app.financepro.core.ui.theme

import androidx.compose.ui.graphics.Color
import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * REQ-DS-006 · REQ-DS-007 · REQ-DS-008 · REQ-A11Y-005
 *
 * Recalcula o contraste **a partir dos tokens**, com a fórmula de luminância
 * relativa da WCAG 2.1. Uma tabela em Markdown mente assim que alguém mexe num
 * hexadecimal; o teste não.
 *
 * O que ele protege não é a tabela — é a regra que a tabela justifica: acento é
 * preenchimento, texto é `ink`. Se um dia alguém "melhorar" a paleta e um acento
 * virar cor de texto, o build cai aqui.
 *
 * **A medição é contra `surface`, não contra `paper`.** É a diferença que a
 * troca de sistema visual trouxe: sobre preto puro os oito acentos passam de
 * 4.5:1 e a regra *parece* desnecessária. Sobre o card — que é onde o conteúdo
 * de fato mora — cinco reprovam. Medir o fundo fácil seria escrever um teste que
 * concorda com o erro.
 */
@Req("REQ-DS-006", "REQ-DS-007", "REQ-DS-008")
class ContrastTest {

    @Test
    fun `nenhum acento serve como cor de texto nos dois temas`() {
        // O ponto inteiro de REQ-DS-006. Teal passa no escuro (5.85) e reprova
        // no claro (2.77); Light Blue faz o inverso, e mal. Não existe
        // subconjunto seguro nos dois — por isso a regra é "nenhum", e não uma
        // tabela por tema que alguém teria de consultar antes de cada `Text`.
        val seguroNosDois = Acentos.filter {
            contrast(it, DarkSlush.surface) >= TEXTO && contrast(it, LightSlush.surface) >= TEXTO
        }

        assertEquals(emptyList<Color>(), seguroNosDois)
    }

    @Test
    fun `tinta sobre canvas e sobre card passa nos dois temas`() {
        // Duas superfícies por tema, e o texto tem de funcionar nas duas: o
        // título fica no canvas, o conteúdo dentro do card.
        listOf(DarkSlush, LightSlush).forEach { tema ->
            assertTrue("ink sobre paper", contrast(tema.ink, tema.paper) >= TEXTO)
            assertTrue("ink sobre surface", contrast(tema.ink, tema.surface) >= TEXTO)
        }
    }

    @Test
    fun `tinta fraca ainda passa nas duas superficies dos dois temas`() {
        // `inkMute` é o subtítulo da linha de transação e o metadado do card. É
        // o token onde a economia de contraste é tentadora, e o pior caso — 6.40
        // no claro sobre o card — ainda tem folga sobre 4.5.
        listOf(DarkSlush, LightSlush).forEach { tema ->
            assertTrue("inkMute sobre paper", contrast(tema.inkMute, tema.paper) >= TEXTO)
            assertTrue("inkMute sobre surface", contrast(tema.inkMute, tema.surface) >= TEXTO)
        }
    }

    @Test
    fun `branco sobre Cobalto passa e sobre Danger reprova`() {
        // O par que parece igual e não é: os dois são preenchimento saturado com
        // texto por cima, e só um deles é legível. Cobalto é o único fundo
        // colorido que aceita texto no sistema inteiro.
        assertTrue(contrast(CanvasLight, Cobalt) >= TEXTO)
        assertTrue(contrast(CanvasLight, Danger) < TEXTO)
    }

    @Test
    fun `acento sozinho nao separa da superficie clara, e por isso leva anel`() {
        // Este é o teste que justifica um desenho, e não o contrário. O ponto de
        // categoria e a amostra do seletor levam anel de `ink` de 1dp porque a
        // cor sozinha não chega aos 3:1 de elemento não textual da WCAG: sobre a
        // superfície clara, Verde-azulado dá 2.77 e Laranja 2.53.
        //
        // Se um dia a paleta escurecer a ponto de as oito passarem, é aqui que
        // se descobre — e aí o anel vira decoração, que é hora de removê-lo.
        val fracos = Acentos.filter { contrast(it, LightSlush.surface) < NAO_TEXTO }

        assertTrue("nenhum acento é fraco: o anel virou decoração", fracos.isNotEmpty())
    }

    @Test
    fun `os dois modos sao canvas opostos, e os acentos nao mudam entre eles`() {
        // REQ-DS-008: o que inverte é o canvas e a tinta. A cor de uma categoria
        // é identidade, e identidade não muda quando anoitece.
        assertEquals(CanvasDark, DarkSlush.paper)
        assertEquals(CanvasLight, DarkSlush.ink)
        assertEquals(CanvasLight, LightSlush.paper)
        assertEquals(InkLight, LightSlush.ink)
        assertEquals(SurfaceElevated, DarkSlush.surface)
        assertEquals(SurfaceSoft, LightSlush.surface)
    }

    @Test
    fun `os numeros medidos em design md continuam valendo`() {
        // Ancora a documentação no código. Se um hex mudar, é aqui que se
        // descobre que a tabela do design.md §5 virou ficção.
        assertEquals(21.00, contrast(CanvasLight, CanvasDark), 0.01)
        assertEquals(17.80, contrast(CanvasLight, SurfaceElevated), 0.01)
        assertEquals(17.11, contrast(InkLight, CanvasLight), 0.01)
        assertEquals(6.06, contrast(CanvasLight, Cobalt), 0.01)
        assertEquals(3.47, contrast(Cobalt, CanvasDark), 0.01)
        assertEquals(5.85, contrast(Teal, SurfaceElevated), 0.01)
        assertEquals(2.77, contrast(Teal, SurfaceSoft), 0.01)
        assertEquals(3.94, contrast(Pink, SurfaceElevated), 0.01)
        assertEquals(4.20, contrast(Danger, SurfaceElevated), 0.01)
    }

    private companion object {
        const val TEXTO = 4.5
        const val NAO_TEXTO = 3.0
    }
}

/** Luminância relativa da WCAG 2.1. */
private fun luminance(c: Color): Double {
    fun canal(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
}

private fun contrast(a: Color, b: Color): Double {
    val (claro, escuro) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (claro + 0.05) / (escuro + 0.05)
}
