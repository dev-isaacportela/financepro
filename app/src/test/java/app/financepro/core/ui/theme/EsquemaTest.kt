package app.financepro.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * REQ-DS-001 · REQ-DS-004 — o esquema do Material não traz cor de fora.
 *
 * `darkColorScheme` e `lightColorScheme` preenchem sozinhos todo papel que não
 * recebe valor, e o padrão deles é o neutro de referência do Material 3, que é
 * **roxeado**: `surfaceContainer` vale `#211F26` e `inverseSurface` é lavanda
 * clara. Basta um componente que ninguém customizou — um `Snackbar`, um menu, um
 * campo preenchido — para o roxo aparecer numa tela que o resto do app pinta em
 * cinza neutro.
 *
 * É o vazamento que não dá erro, não aparece no arquivo do componente e some da
 * memória de quem viu uma vez. Este teste é o que o transforma em build vermelho.
 *
 * A lista de papéis é escrita à mão de propósito. Reflexão pegaria os campos
 * novos que o Material inventar, mas exigiria `kotlin-reflect` só para isto — e
 * um papel novo do Material entra no app junto com uma atualização de
 * biblioteca, que é exatamente quando alguém lê a lista de mudanças.
 */
@Req("REQ-DS-001", "REQ-DS-004")
class EsquemaTest {

    @Test
    fun `o esquema escuro so usa cores da paleta`() {
        conferir(PaletaEscura, PaletaEscura.toDarkScheme())
    }

    @Test
    fun `o esquema claro so usa cores da paleta`() {
        conferir(PaletaClara, PaletaClara.toLightScheme())
    }

    private fun conferir(paleta: Paleta, esquema: ColorScheme) {
        val permitidas = setOf(
            paleta.paper,
            paleta.surface,
            paleta.ink,
            paleta.inkMute,
            paleta.hairline,
            CanvasDark,
            Danger,
        )

        val papeis = mapOf(
            "primary" to esquema.primary,
            "onPrimary" to esquema.onPrimary,
            "primaryContainer" to esquema.primaryContainer,
            "onPrimaryContainer" to esquema.onPrimaryContainer,
            "secondary" to esquema.secondary,
            "onSecondary" to esquema.onSecondary,
            "secondaryContainer" to esquema.secondaryContainer,
            "onSecondaryContainer" to esquema.onSecondaryContainer,
            "tertiary" to esquema.tertiary,
            "onTertiary" to esquema.onTertiary,
            "tertiaryContainer" to esquema.tertiaryContainer,
            "onTertiaryContainer" to esquema.onTertiaryContainer,
            "background" to esquema.background,
            "onBackground" to esquema.onBackground,
            "surface" to esquema.surface,
            "onSurface" to esquema.onSurface,
            "surfaceVariant" to esquema.surfaceVariant,
            "onSurfaceVariant" to esquema.onSurfaceVariant,
            "surfaceTint" to esquema.surfaceTint,
            "inverseSurface" to esquema.inverseSurface,
            "inverseOnSurface" to esquema.inverseOnSurface,
            "inversePrimary" to esquema.inversePrimary,
            "error" to esquema.error,
            "onError" to esquema.onError,
            "errorContainer" to esquema.errorContainer,
            "onErrorContainer" to esquema.onErrorContainer,
            "outline" to esquema.outline,
            "outlineVariant" to esquema.outlineVariant,
            "scrim" to esquema.scrim,
            "surfaceBright" to esquema.surfaceBright,
            "surfaceDim" to esquema.surfaceDim,
            "surfaceContainer" to esquema.surfaceContainer,
            "surfaceContainerHigh" to esquema.surfaceContainerHigh,
            "surfaceContainerHighest" to esquema.surfaceContainerHighest,
            "surfaceContainerLow" to esquema.surfaceContainerLow,
            "surfaceContainerLowest" to esquema.surfaceContainerLowest,
        )

        val forasteiras = papeis.filterValues { it !in permitidas }.map { (papel, cor) ->
            "$papel = #%08X".format(cor.toArgbInt())
        }

        assertEquals(emptyList<String>(), forasteiras)
    }
}

/** `Color.toArgb()` mora no artefato de UI; aqui basta o inteiro para a mensagem. */
private fun Color.toArgbInt(): Int =
    (alpha * 255).toInt().shl(24) or
        (red * 255).toInt().shl(16) or
        (green * 255).toInt().shl(8) or
        (blue * 255).toInt()
