package app.financepro.core.ui.theme

import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnitType
import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-DS-005 — a entrelinha travada.
 *
 * Este é o teste que mais rende do sistema visual, porque vigia um erro que
 * **não parece um erro**: `includeFontPadding` volta a `true` com uma linha
 * distraída, o Compose reintroduz a folga de métrica da fonte, e o sintoma é
 * "o título ficou espaçado demais" — que ninguém procura no código.
 *
 * O mecanismo sobreviveu à troca de sistema visual intacto; só o número que ele
 * protege mudou, de `0.78em` para `1.0em`.
 */
@Req("REQ-DS-005")
class TypographyTest {

    @Test
    fun `os tres estilos display tem a entrelinha travada em um`() {
        DisplayStyles.forEach { estilo ->
            assertEquals(TextUnitType.Em, estilo.lineHeight.type)
            assertEquals("lineHeight ${estilo.lineHeight}", TRAVA, estilo.lineHeight.value, 0.001f)
        }
    }

    @Test
    fun `os tres display desligam a folga de metrica e cortam nas duas pontas`() {
        // Sem os dois juntos o lineHeight de 1.0em rende como parágrafo comum, e
        // o empilhamento apertado — a razão de a regra existir — some em silêncio.
        DisplayStyles.forEach { estilo ->
            assertEquals(false, estilo.platformStyle?.paragraphStyle?.includeFontPadding)
            assertEquals(LineHeightStyle.Trim.Both, estilo.lineHeightStyle?.trim)
        }
    }

    @Test
    fun `a escala e a da spec`() {
        assertEquals(64f, DisplayXl.fontSize.value, 0f)
        assertEquals(44f, Display.fontSize.value, 0f)
        assertEquals(34f, DisplaySm.fontSize.value, 0f)
    }

    @Test
    fun `a entreletra do display e negativa e aperta conforme o tamanho cresce`() {
        // O ajuste que separa tipo grande de tipo apenas ampliado. Sem ele um
        // título de 64sp lê como banner; e um valor fixo para os três deixaria o
        // menor apertado demais.
        DisplayStyles.forEach { assertTrue("${it.fontSize}", it.letterSpacing.value < 0f) }
        assertTrue(DisplayXl.letterSpacing.value < Display.letterSpacing.value)
        assertTrue(Display.letterSpacing.value < DisplaySm.letterSpacing.value)
    }

    @Test
    fun `o corpo tem entreletra positiva, que e o oposto do display`() {
        // A precisão mecânica que o sistema pede vem daqui: a mesma frase com
        // tracking zero lê como texto de artigo. É o par do aperto do display, e
        // trocar o sinal por descuido desmonta os dois de uma vez.
        assertTrue(Body.letterSpacing.value > 0f)
        assertTrue(BodyLg.letterSpacing.value > 0f)
    }

    @Test
    fun `lineHeight em em, para a proporcao sobreviver a fonte de 200 por cento`() {
        // REQ-A11Y-004. Em `sp`, o corpo cresceria e o leading não — a 200% o
        // display se sobreporia a si mesmo em vez de só ficar mais alto.
        (DisplayStyles + listOf(HeadingSm, Subheading, BodyLg, Body, Caption, Label)).forEach {
            assertEquals(TextUnitType.Em, it.lineHeight.type)
        }
    }

    @Test
    fun `todo estilo de dinheiro pede algarismos tabulares`() {
        // REQ-DS-007. Sem `tnum` a coluna de valores não alinha na vertical.
        listOf(MoneyLg, MoneyBody, MoneyCaption).forEach {
            assertEquals("tnum", it.fontFeatureSettings)
        }
    }

    @Test
    fun `o valor grande nao herda a entreletra positiva do corpo`() {
        // `MoneyBody` é `Body` com peso — e `Body` tem tracking positivo, que num
        // número com `tnum` afasta os algarismos e desfaz o alinhamento que o
        // `tnum` acabou de garantir.
        assertEquals(0f, MoneyBody.letterSpacing.value, 0f)
    }

    private companion object {
        const val TRAVA = 1.0f
    }
}
