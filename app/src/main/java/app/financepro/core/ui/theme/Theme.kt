package app.financepro.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * O tema. REQ-UI-007
 *
 * Sem cor dinâmica (Material You): a paleta é de marca, e derivar cor do papel de
 * parede sobrescreveria os tokens e destruiria as garantias de contraste, que
 * dependem de hexadecimais conhecidos.
 */
val LocalPaleta = staticCompositionLocalOf { PaletaEscura }

/**
 * Atalho de leitura: `Tema.ink` em vez de `LocalPaleta.current.ink`.
 *
 * Não é açúcar gratuito — o token aparece em toda superfície, e a versão longa
 * repetida três vezes numa assinatura de componente empurra a linha para além dos
 * 120 caracteres e convida alguém a passar a cor por parâmetro.
 */
val Tema: Paleta
    @Composable get() = LocalPaleta.current

@Composable
fun FinanceProTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val paleta = if (dark) PaletaEscura else PaletaClara
    CompositionLocalProvider(LocalPaleta provides paleta) {
        MaterialTheme(
            // O esquema do Material existe para que um componente que ninguém
            // customizou já nasça no canvas certo, em vez do roxo padrão.
            colorScheme = if (dark) paleta.toDarkScheme() else paleta.toLightScheme(),
            typography = Tipografia,
            shapes = Formas,
            content = content,
        )
    }
}

/**
 * `primary` é a **tinta**, e `onPrimary` o canvas — é o que faz o botão primário
 * nascer pílula branca sobre preto, que é a ação mais forte do sistema. Cobalto
 * não entra aqui de propósito: ele é preenchimento de um card em destaque por
 * tela, e no slot `primary` viraria a cor de todo botão do app.
 *
 * `surface` é o degrau único acima do canvas, e `surfaceVariant` repete ele: no
 * Material o segundo existe para dar tom a superfícies aninhadas, e um terceiro
 * tom é exatamente o que REQ-DS-004 proíbe.
 *
 * `error` é Danger porque é a cor do traço indicador de campo inválido. **Não é
 * cor de mensagem**: texto Danger sobre o card dá 4.20:1 e reprova, então o erro
 * escrito continua em `ink`, com palavras (REQ-DS-007, REQ-A11Y-003).
 *
 * **Todos os papéis são preenchidos, inclusive os que o app não usa.** O que
 * `darkColorScheme` não recebe cai no neutro de referência do Material 3, que é
 * **roxeado** — `surfaceContainer` vale `#211F26`, e `inverseSurface` é lavanda
 * clara. Basta um componente que ninguém customizou — um `Snackbar`, um
 * `TextField` preenchido, um menu — para o roxo aparecer numa tela que o resto
 * do app pinta em cinza neutro.
 *
 * É o tipo de vazamento que não dá erro e não aparece no arquivo onde o
 * componente está: aparece na tela, uma vez, e some da memória de quem viu.
 * Preencher os cinco degraus de `surfaceContainer` com o mesmo `surface` custa
 * dez linhas e fecha a porta inteira.
 */
internal fun Paleta.toDarkScheme() = darkColorScheme(
    primary = ink,
    onPrimary = paper,
    secondary = ink,
    onSecondary = paper,
    background = paper,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = surface,
    onSurfaceVariant = inkMute,
    outline = hairline,
    outlineVariant = hairline,

    // Os cinco degraus de container do Material viram o degrau único deste
    // sistema: a escada aqui tem dois passos e para (REQ-DS-004).
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surface,
    surfaceContainerHighest = surface,
    surfaceBright = surface,
    surfaceDim = paper,

    // `surfaceTint` é o que o Material mistura ao fundo quando há elevação.
    // Igualando a `surface`, a mistura é consigo mesma e não tinge nada — que é
    // o mesmo resultado de `tonalElevation = 0`, mas garantido para componente
    // que ninguém customizou.
    surfaceTint = surface,

    // Fundo invertido, do `Snackbar`. Sem isto ele nasce lavanda claro.
    inverseSurface = ink,
    inverseOnSurface = paper,
    inversePrimary = paper,
    scrim = CanvasDark,

    // Não usados hoje, e preenchidos justamente por isso: o dia em que alguém
    // largar um componente novo na tela, ele nasce na paleta da casa.
    tertiary = ink,
    onTertiary = paper,
    primaryContainer = surface,
    onPrimaryContainer = ink,
    secondaryContainer = surface,
    onSecondaryContainer = ink,
    tertiaryContainer = surface,
    onTertiaryContainer = ink,
    errorContainer = surface,
    onErrorContainer = ink,

    error = Danger,
    onError = ink,
)

internal fun Paleta.toLightScheme() = lightColorScheme(
    primary = ink,
    onPrimary = paper,
    secondary = ink,
    onSecondary = paper,
    background = paper,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = surface,
    onSurfaceVariant = inkMute,
    outline = hairline,
    outlineVariant = hairline,

    // Os cinco degraus de container do Material viram o degrau único deste
    // sistema: a escada aqui tem dois passos e para (REQ-DS-004).
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surface,
    surfaceContainerHighest = surface,
    surfaceBright = surface,
    surfaceDim = paper,

    // `surfaceTint` é o que o Material mistura ao fundo quando há elevação.
    // Igualando a `surface`, a mistura é consigo mesma e não tinge nada — que é
    // o mesmo resultado de `tonalElevation = 0`, mas garantido para componente
    // que ninguém customizou.
    surfaceTint = surface,

    // Fundo invertido, do `Snackbar`. Sem isto ele nasce lavanda claro.
    inverseSurface = ink,
    inverseOnSurface = paper,
    inversePrimary = paper,
    scrim = CanvasDark,

    // Não usados hoje, e preenchidos justamente por isso: o dia em que alguém
    // largar um componente novo na tela, ele nasce na paleta da casa.
    tertiary = ink,
    onTertiary = paper,
    primaryContainer = surface,
    onPrimaryContainer = ink,
    secondaryContainer = surface,
    onSecondaryContainer = ink,
    tertiaryContainer = surface,
    onTertiaryContainer = ink,
    errorContainer = surface,
    onErrorContainer = ink,

    error = Danger,
    onError = paper,
)
