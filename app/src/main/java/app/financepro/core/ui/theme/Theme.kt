package app.financepro.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tema Slush. REQ-UI-007
 *
 * Sem cor dinâmica (Material You): a paleta é de marca, e derivar cor do papel de
 * parede sobrescreveria os tokens e destruiria as garantias de contraste, que
 * dependem de hexadecimais conhecidos.
 */
val LocalSlush = staticCompositionLocalOf { LightSlush }

/**
 * Atalho de leitura: `Slush.ink` em vez de `LocalSlush.current.ink`.
 *
 * Não é açúcar gratuito — o contorno aparece em toda superfície, e a versão longa
 * repetida três vezes numa assinatura de componente empurra a linha para além dos
 * 120 caracteres e convida alguém a passar a cor por parâmetro.
 */
val Slush: SlushColors
    @Composable get() = LocalSlush.current

@Composable
fun SlushTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val slush = if (dark) DarkSlush else LightSlush
    CompositionLocalProvider(LocalSlush provides slush) {
        MaterialTheme(
            // O esquema do Material existe para que um componente que ninguém
            // customizou já nasça em papel e tinta, em vez do roxo padrão.
            colorScheme = if (dark) slush.toDarkScheme() else slush.toLightScheme(),
            typography = SlushTypography,
            shapes = SlushShapes,
            content = content,
        )
    }
}

/**
 * `primary` é a tinta, não uma cor de marca saturada: o botão primário de Slush é
 * preenchimento Carbon com texto Paper White, nunca azul. `surfaceVariant` também
 * é papel — no Material ele existe para dar tom a superfícies aninhadas, e tom é
 * exatamente o que REQ-DS-004 proíbe.
 */
private fun SlushColors.toLightScheme() = lightColorScheme(
    primary = ink,
    onPrimary = paper,
    secondary = ink,
    onSecondary = paper,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = paper,
    onSurfaceVariant = ink,
    outline = ink,
    outlineVariant = ink,
    error = Ember,
    onError = paper,
)

private fun SlushColors.toDarkScheme() = darkColorScheme(
    primary = ink,
    onPrimary = paper,
    secondary = ink,
    onSecondary = paper,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = paper,
    onSurfaceVariant = ink,
    outline = ink,
    outlineVariant = ink,
    error = Ember,
    onError = ink,
)
