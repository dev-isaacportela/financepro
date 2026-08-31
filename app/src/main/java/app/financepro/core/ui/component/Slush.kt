package app.financepro.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.theme.MoneyBody
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes

/**
 * Componentes base. REQ-DS-002 · REQ-DS-004 · REQ-A11Y-002 ·
 * [design.md](../../../../../../../../../docs/design.md) §6
 *
 * Existem por um motivo só: **sombra é o erro fácil aqui**. `Card`, `Button`,
 * `FloatingActionButton` e `Surface` do Material 3 trazem elevação por padrão, e
 * `Surface` ainda aplica *tonal elevation*, que tinge o fundo mesmo sem sombra
 * visível. Zerar os quatro em cada tela é uma linha para esquecer; zerar aqui é
 * uma vez.
 *
 * Todos nascem com contorno `ink` de 1dp e alvo de toque de 48dp. A
 * acessibilidade vence o token visual: o padding de Slush é menor, então o alvo é
 * ampliado por `minimumInteractiveComponentSize` sem mexer no desenho.
 */

/** Ação primária: preenchimento `ink`, texto `paper`. Nunca azul. */
@Composable
fun FilledCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        enabled = enabled,
        shape = SlushShapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = Slush.ink,
            contentColor = Slush.paper,
        ),
        elevation = null, // sem sombra, nunca
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}

/** Ação secundária, chips de nav e tags: pill contornada sobre papel. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        enabled = enabled,
        shape = Pill,
        border = BorderStroke(OutlineWidth, Slush.ink),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Slush.paper,
            contentColor = Slush.ink,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}

@Composable
fun SlushCard(
    modifier: Modifier = Modifier,
    shape: Shape = SlushShapes.small,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = BorderStroke(OutlineWidth, Slush.ink),
        colors = CardDefaults.cardColors(containerColor = Slush.paper, contentColor = Slush.ink),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

/** `tonalElevation = 0` é o que importa: sem ele o Material tinge o papel. */
@Composable
fun SlushSurface(
    modifier: Modifier = Modifier,
    shape: Shape = SlushShapes.large,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Slush.paper,
        contentColor = Slush.ink,
        border = BorderStroke(OutlineWidth, Slush.ink),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        content()
    }
}

@Composable
fun SlushFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = Pill,
        containerColor = Slush.ink,
        contentColor = Slush.paper,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
        content = content,
    )
}

/**
 * Todo valor monetário da tela passa por aqui. REQ-DS-007 · REQ-CORE-005
 *
 * Centraliza três coisas que nenhuma tela deve refazer: a formatação pt-BR de
 * [formatBRL], o sinal `−` (U+2212, não hífen) e os algarismos tabulares. Sem
 * `tnum` os valores não alinham na vertical, e uma coluna de dinheiro
 * desalinhada é mais difícil de conferir contra o extrato.
 *
 * A cor é sempre `ink`. Receita e despesa se distinguem pelo **sinal** e pelo
 * rótulo da categoria, nunca por verde e vermelho (REQ-A11Y-003).
 */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyBody,
) {
    Text(
        text = formatBRL(cents),
        modifier = modifier,
        color = Slush.ink,
        style = style,
    )
}
