package com.benenutri.finance.core.ui.theme

import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnitType
import com.benenutri.finance.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-DS-005 — o leading esmagado.
 *
 * Este é o teste que mais rende do sistema visual, porque vigia um erro que
 * **não parece um erro**: `includeFontPadding` volta a `true` com uma linha
 * distraída, o Compose reintroduz a folga de métrica da fonte, e o sintoma é
 * "o título ficou espaçado demais" — que ninguém procura no código.
 */
@Req("REQ-DS-005")
class TypographyTest {

    @Test
    fun `os tres estilos display tem leading esmagado`() {
        DisplayStyles.forEach { estilo ->
            assertEquals(TextUnitType.Em, estilo.lineHeight.type)
            assertTrue("lineHeight ${estilo.lineHeight} > 0.85em", estilo.lineHeight.value <= LIMITE)
        }
    }

    @Test
    fun `os tres display desligam a folga de metrica e cortam nas duas pontas`() {
        // Sem os dois juntos o lineHeight de 0.78em não aparece na tela.
        DisplayStyles.forEach { estilo ->
            assertEquals(false, estilo.platformStyle?.paragraphStyle?.includeFontPadding)
            assertEquals(LineHeightStyle.Trim.Both, estilo.lineHeightStyle?.trim)
        }
    }

    @Test
    fun `a escala e a da spec`() {
        assertEquals(88f, DisplayXl.fontSize.value, 0f)
        assertEquals(0.78f, DisplayXl.lineHeight.value, 0.001f)
        assertEquals(64f, Display.fontSize.value, 0f)
        assertEquals(0.80f, Display.lineHeight.value, 0.001f)
        assertEquals(44f, DisplaySm.fontSize.value, 0f)
        assertEquals(0.82f, DisplaySm.lineHeight.value, 0.001f)
    }

    @Test
    fun `lineHeight em em, para a proporcao sobreviver a fonte de 200 por cento`() {
        // REQ-A11Y-004. Em `sp`, o corpo cresceria e o leading não — a 200% o
        // display se sobreporia a si mesmo em vez de só ficar mais alto.
        (DisplayStyles + listOf(HeadingSm, Subheading, BodyLg, Caption)).forEach {
            assertEquals(TextUnitType.Em, it.lineHeight.type)
        }
    }

    @Test
    fun `todo estilo de dinheiro pede algarismos tabulares`() {
        // REQ-DS-007. Sem `tnum` a coluna de valores não alinha na vertical.
        listOf(MoneyLg, MoneyBody).forEach {
            assertEquals("tnum", it.fontFeatureSettings)
        }
    }

    private companion object {
        const val LIMITE = 0.85f
    }
}
