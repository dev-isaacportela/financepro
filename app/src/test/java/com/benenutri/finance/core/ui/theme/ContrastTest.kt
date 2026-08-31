package com.benenutri.finance.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.benenutri.finance.core.testing.Req
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
 * O que ele protege não é a tabela — é a regra que a tabela justifica: sticker é
 * preenchimento, texto é `ink`. Se um dia alguém "melhorar" a paleta e um sticker
 * virar cor de texto, o build cai aqui.
 */
@Req("REQ-DS-006", "REQ-DS-007", "REQ-DS-008")
class ContrastTest {

    @Test
    fun `nenhum sticker serve como cor de texto nos dois temas`() {
        // O ponto inteiro de REQ-DS-006. Sobre branco só o Violet passa; sobre
        // papel escuro é justamente o Violet que reprova. Não existe subconjunto
        // seguro nos dois — por isso a regra é "nenhum", e não uma tabela por tema.
        val seguraNosDois = Stickers.filter {
            contrast(it, LightSlush.paper) >= TEXTO && contrast(it, DarkSlush.paper) >= TEXTO
        }

        assertEquals(emptyList<Color>(), seguraNosDois)
    }

    @Test
    fun `tinta sobre papel passa com folga nos dois temas`() {
        assertTrue(contrast(LightSlush.ink, LightSlush.paper) >= TEXTO)
        assertTrue(contrast(DarkSlush.ink, DarkSlush.paper) >= TEXTO)
    }

    @Test
    fun `tinta sobre as bandas passa nos dois temas`() {
        // As bandas são fundo de seção com texto por cima. Se uma delas escurecer
        // no claro, ou clarear no escuro, o texto some — e é o único lugar da
        // paleta onde texto encosta em cor.
        listOf(LightSlush, DarkSlush).forEach { tema ->
            listOf(tema.bandSky, tema.bandNeutral, tema.bandLavender).forEach { banda ->
                assertTrue("$banda reprova contra ${tema.ink}", contrast(tema.ink, banda) >= TEXTO)
            }
        }
    }

    @Test
    fun `branco sobre Voltage Violet passa e sobre Ember reprova`() {
        // O padrão do card QR de Slush. O teste registra os dois lados: o que é
        // permitido e o que parece igual mas não é.
        assertTrue(contrast(PaperWhite, VoltageViolet) >= TEXTO)
        assertTrue(contrast(PaperWhite, Ember) < TEXTO)
    }

    @Test
    fun `tema escuro inverte o papel e mantem os stickers identicos`() {
        // REQ-DS-008: a gramática do adesivo é a mesma; o que inverte é o papel.
        assertEquals(PaperWhite, LightSlush.paper)
        assertEquals(Carbon, LightSlush.ink)
        assertEquals(CarbonPaper, DarkSlush.paper)
        assertEquals(PaperWhite, DarkSlush.ink)
        assertEquals(LightSlush.onFill, DarkSlush.onFill)
    }

    @Test
    fun `os numeros medidos em design md continuam valendo`() {
        // Ancora a documentação no código. Se um hex mudar, é aqui que se
        // descobre que a tabela do design.md §5 virou ficção.
        assertEquals(21.00, contrast(Carbon, PaperWhite), 0.01)
        assertEquals(6.02, contrast(VoltageViolet, PaperWhite), 0.01)
        assertEquals(3.47, contrast(Ember, PaperWhite), 0.01)
        assertEquals(2.65, contrast(ElectricBlue, PaperWhite), 0.01)
        assertEquals(1.75, contrast(MintPop, PaperWhite), 0.01)
        assertEquals(1.40, contrast(Sunburst, PaperWhite), 0.01)
        assertEquals(13.50, contrast(Sunburst, CarbonPaper), 0.01)
        assertEquals(3.14, contrast(VoltageViolet, CarbonPaper), 0.01)
    }

    private companion object {
        const val TEXTO = 4.5
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
