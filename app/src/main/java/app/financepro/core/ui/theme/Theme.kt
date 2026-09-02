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
val LocalSlush = staticCompositionLocalOf { DarkSlush }

/**
 * Atalho de leitura: `Slush.ink` em vez de `LocalSlush.current.ink`.
 *
 * Não é açúcar gratuito — o token aparece em toda superfície, e a versão longa
 * repetida três vezes numa assinatura de componente empurra a linha para além dos
 * 120 caracteres e convida alguém a passar a cor por parâmetro.
 *
 * ponytail: o nome `Slush` sobreviveu à troca de sistema visual e hoje mente —
 * ele veio do style reference anterior. Renomear é `sed` mecânico em 21 arquivos
 * e zero mudança de comportamento; fazer quando não estiver empilhado sobre
 * outra mudança grande, para o diff da renomeação ser lido como renomeação.
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
            // customizou já nasça no canvas certo, em vez do roxo padrão.
            colorScheme = if (dark) slush.toDarkScheme() else slush.toLightScheme(),
            typography = SlushTypography,
            shapes = SlushShapes,
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
 */
private fun SlushColors.toDarkScheme() = darkColorScheme(
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
    error = Danger,
    onError = ink,
)

private fun SlushColors.toLightScheme() = lightColorScheme(
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
    error = Danger,
    onError = paper,
)
