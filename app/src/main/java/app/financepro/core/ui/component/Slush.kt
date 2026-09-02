package app.financepro.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.financepro.core.money.formatBRL
import app.financepro.core.money.spokenBRL
import app.financepro.core.ui.theme.BodyLg
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Display
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

/**
 * [rotulo] é obrigatório de propósito. O conteúdo de um FAB é um glifo — o
 * nosso é um `+` —, e "sinal de adição" não diz a ninguém o que o botão faz
 * (REQ-A11Y-001). Sendo parâmetro e não `Modifier` opcional, não há como
 * esquecê-lo no próximo chamador.
 */
@Composable
fun SlushFab(
    onClick: () -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = rotulo },
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
 * [cor] existe por **um** motivo: valor sobre preenchimento saturado, onde a
 * tinta obrigatória é `onFill` — branco sobre Voltage Violet passa em 6.02:1, e
 * `ink` no tema claro reprovaria em 3.5:1. Não é porta para verde e vermelho:
 * receita e despesa continuam se distinguindo pelo **sinal** e pelo rótulo da
 * categoria, nunca por cor (REQ-A11Y-003), e `ContrastTest` guarda o resto.
 *
 * E é aqui que REQ-A11Y-006 se resolve de uma vez: a `contentDescription` traz
 * o valor por extenso, de [spokenBRL]. O texto na tela continua `−R$ 18,50`,
 * que é o que o olho quer; o leitor de tela ouve "menos dezoito reais e
 * cinquenta centavos" em vez de "traço erre cifrão dezoito vírgula cinquenta".
 *
 * Corrigir isto no componente e não em cada tela é a razão de o KDoc acima
 * cobrar que todo valor passe por aqui — trinta chamadas, uma correção.
 */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyBody,
    cor: Color = Slush.ink,
) {
    val falado = spokenBRL(cents)
    Text(
        text = formatBRL(cents),
        modifier = modifier.semantics { contentDescription = falado },
        color = cor,
        style = style,
    )
}

/**
 * Fileira de escolha única. REQ-A11Y-003
 *
 * A seleção é sinalizada por **preenchimento** (`FilledCta` no lugar de
 * `GhostButton`), não por cor — a mesma gramática da barra de navegação, e o
 * que faz a escolha sobreviver a daltonismo e a tema escuro sem condicional.
 *
 * Nasceu privada no formulário de conta (T-015). Subiu quando os filtros de
 * transação (T-014) viraram o **segundo chamador real** — que é o que o Art. 10
 * exige antes de promover qualquer coisa. Reescrevê-la lá criaria dois chips
 * para divergirem no primeiro ajuste de espaçamento.
 */
@Composable
fun <T> Chips(itens: List<Pair<T, String>>, selecionado: T?, onClick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(itens, key = { it.first.toString() }) { (valor, texto) ->
            if (valor == selecionado) {
                FilledCta(text = texto, onClick = { onClick(valor) })
            } else {
                GhostButton(text = texto, onClick = { onClick(valor) })
            }
        }
    }
}

/**
 * Estado vazio. REQ-UI-006 · REQ-DS-009 ·
 * [design.md](../../../../../../../../../docs/design.md) §1
 *
 * **Tela vazia é pôster, não frase.** A tabela de intensidade manda `display` e um
 * sticker aqui, e o token `Display` existe desde a T-002 com o comentário "estados
 * vazios" — sem nenhum chamador. Uma linha de `Body` no meio da tela não é estado
 * vazio discreto: é tela que parece quebrada, e é justamente o momento em que o app
 * tem menos dado para mostrar e mais espaço para ter identidade.
 *
 * O sticker assenta com uma mola em vez de aparecer pronto. É a única coisa que se
 * mexe numa tela que, por definição, não tem conteúdo — e quem desliga animações
 * nas opções de acessibilidade recebe ele já assentado, porque o Compose lê a
 * escala de animação do sistema sem ninguém aqui perguntar.
 *
 * O botão **não** entra aqui: REQ-UI-006 pede a ação que preenche o vazio, e cada
 * tela já tem a sua, com o verbo certo ("Criar a primeira", "Lançar", "Limpar
 * filtros"). Um parâmetro de ação genérico só daria a todas o mesmo rótulo morno.
 *
 * [titulo] vem em caixa alta do chamador, como no onboarding: o display type de
 * Slush é caixa alta, e forçar `uppercase()` aqui esconderia a decisão de quem lê
 * a tela. Sem `maxLines` de propósito — display que não cabe quebra, nunca vira
 * reticências (REQ-DS-005), e a 200% de fonte ele cresce.
 */
@Composable
fun EstadoVazio(
    titulo: String,
    sticker: Color,
    modifier: Modifier = Modifier,
    descricao: String? = null,
) {
    val escala = remember { Animatable(VAZIO_ESCALA) }
    LaunchedEffect(Unit) {
        escala.animateTo(
            targetValue = 1f,
            animationSpec = spring(VAZIO_AMORTECIMENTO, Spring.StiffnessMediumLow),
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Box sem semântica: é decoração, e o leitor de tela já recebe o título
        // logo abaixo. Um `contentDescription` aqui seria "quadrado amarelo".
        Box(
            Modifier
                .size(STICKER_VAZIO)
                .scale(escala.value)
                .clip(SlushShapes.small)
                .background(sticker)
                .border(OutlineWidth, Slush.ink, SlushShapes.small),
        )
        Text(text = titulo, style = Display, color = Slush.ink)
        if (descricao != null) Text(text = descricao, style = BodyLg, color = Slush.ink)
    }
}

/** O mesmo 64dp do sticker de categoria: é o tamanho de adesivo do app. */
private val STICKER_VAZIO = 64.dp
private const val VAZIO_ESCALA = 0.86f
private const val VAZIO_AMORTECIMENTO = 0.5f

/** Rótulo de campo. Existe para nenhuma tela escolher o estilo por conta própria. */
@Composable
fun Rotulo(texto: String) = Text(texto, style = Caption, color = Slush.ink)
