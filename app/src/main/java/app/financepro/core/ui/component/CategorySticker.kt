package app.financepro.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.theme.CanvasDark
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.Tema
import app.financepro.domain.model.Category

/**
 * Sticker de categoria. REQ-CAT-006 · REQ-A11Y-002 · REQ-A11Y-003 ·
 * [design.md](../../../../../../../../../docs/design.md) §6.2
 *
 * É onde a paleta de seis cores mais rende: cada categoria já tem cor e ícone
 * próprios, então o grid vira literalmente uma cartela de adesivos, sem inventar
 * nada.
 *
 * **Seleção é um anel de `ink`, não a cor.** A cor já é a identidade da
 * categoria; usá-la também para estado a tornaria sinal único duplamente
 * sobrecarregado, e quem não distingue as nove cores perderia as duas
 * informações de uma vez. Não selecionado não tem anel nenhum — a presença do
 * contorno é o sinal, e presença lê melhor que espessura.
 *
 * O nome fica **fora** do preenchimento porque a paleta é preenchimento e nunca
 * cor de texto (REQ-DS-006): sobre o card, seis dos nove acentos reprovam em
 * 4.5:1, e Rosa daria 3.94:1.
 *
 * **O toque afunda o quadrado, e não borra tinta nele.** O ripple do Material é
 * uma mancha tonal — o mesmo erro de categoria que a sombra é aqui, e um sinal
 * cinza por cima de Laranja não lê como resposta, lê como sujeira. A escala
 * substitui a indicação em vez de simplesmente apagá-la: pressionado encolhe,
 * como adesivo inflado que cede ao dedo; focado por teclado ou D-pad **cresce**,
 * que é o único sinal que sobraria sem o ripple para quem não usa o dedo.
 */
@Composable
fun CategorySticker(
    category: Category,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interacoes = remember { MutableInteractionSource() }
    val pressionado by interacoes.collectIsPressedAsState()
    val focado by interacoes.collectIsFocusedAsState()
    val escala by animateFloatAsState(
        targetValue = when {
            pressionado -> AFUNDA
            focado -> CRESCE
            else -> 1f
        },
        animationSpec = spring(AMORTECIMENTO, Spring.StiffnessMediumLow),
    )

    Column(
        modifier = modifier
            .width(STICKER)
            .clickable(
                interactionSource = interacoes,
                indication = null, // a escala é a indicação
                onClick = onClick,
            )
            // O leitor anuncia nome e estado uma vez só. O parâmetro se chama
            // `selecionado` porque `selected` sombreia a propriedade de
            // semântica aqui dentro, e a atribuição viraria `val = val`.
            .clearAndSetSemantics {
                contentDescription = category.name
                selected = selecionado
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(STICKER)
                // Só o quadrado se mexe: o nome embaixo fica parado, porque
                // texto que encolhe no toque é texto que fica difícil de ler
                // exatamente enquanto o dedo o cobre.
                .scale(escala)
                .clip(Formas.medium)
                .background(Color(category.colorArgb))
                // `then` e não `border(0.dp)`: largura zero **não** é ausência de
                // borda. O Compose repassa `Stroke(0f)`, que o Skia desenha como
                // linha de um pixel — o adesivo não selecionado ficava com um
                // anel branco fino que ninguém pediu, e o código dizia zero.
                .then(
                    if (selecionado) {
                        Modifier.border(SELECIONADO, Tema.ink, Formas.medium)
                    } else {
                        Modifier
                    },
                ),
        ) {
            // O desenho da categoria, e não só a cor: cinco quadrados coloridos
            // lado a lado obrigam a ler o nome embaixo de cada um para escolher.
            // Com o ícone, a cartela vira reconhecível de relance — que é a
            // razão de o lançamento rápido existir (Art. 18).
            Icone(
                id = iconeDaCategoria(category.iconKey),
                descricao = null,
                modifier = Modifier.size(GLIFO_STICKER),
                tint = CanvasDark,
            )
        }
        Text(
            text = category.name,
            style = Caption,
            color = Tema.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** 64dp já passa dos 48dp de alvo mínimo sem `minimumInteractiveComponentSize`. */
private val STICKER = 64.dp

/** O glifo ocupa pouco mais de um terço do adesivo, como no avatar da lista. */
private val GLIFO_STICKER = 26.dp
private val SELECIONADO = 3.dp
private const val AFUNDA = 0.9f
private const val CRESCE = 1.06f
private const val AMORTECIMENTO = 0.55f
