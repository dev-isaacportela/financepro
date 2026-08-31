package app.financepro.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes
import app.financepro.domain.model.Category

/**
 * Sticker de categoria. REQ-CAT-006 · REQ-A11Y-002 · REQ-A11Y-003 ·
 * [design.md](../../../../../../../../../docs/design.md) §6.2
 *
 * É onde a paleta de seis cores mais rende: cada categoria já tem cor e ícone
 * próprios, então o grid vira literalmente uma cartela de adesivos, sem inventar
 * nada.
 *
 * **Seleção é a espessura do contorno, não a cor.** A cor já é a identidade da
 * categoria; usá-la também para estado a tornaria sinal único duplamente
 * sobrecarregado, e quem não distingue as seis cores perderia as duas
 * informações de uma vez.
 *
 * O nome fica **fora** do preenchimento porque a paleta é preenchimento e nunca
 * cor de texto (REQ-DS-006): "Mercado" em Carbon sobre Sunburst daria 1.40:1.
 */
@Composable
fun CategorySticker(
    category: Category,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(STICKER)
            .clickable(onClick = onClick)
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
            Modifier
                .size(STICKER)
                .clip(SlushShapes.small)
                .background(Color(category.colorArgb))
                .border(
                    width = if (selecionado) SELECIONADO else OutlineWidth,
                    color = Slush.ink,
                    shape = SlushShapes.small,
                ),
        )
        Text(
            text = category.name,
            style = Caption,
            color = Slush.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** 64dp já passa dos 48dp de alvo mínimo sem `minimumInteractiveComponentSize`. */
private val STICKER = 64.dp
private val SELECIONADO = 3.dp
