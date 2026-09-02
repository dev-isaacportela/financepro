package app.financepro.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.theme.Acentos
import app.financepro.core.ui.theme.CanvasDark
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.NomesDeAcento
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Tema

/**
 * Os dois seletores de aparência: cor e ícone. REQ-CAT-001 · REQ-ACC-001 ·
 * REQ-A11Y-001 · REQ-A11Y-003
 *
 * Nasceram privados no formulário de conta e subiram quando o formulário de
 * categoria virou o **segundo chamador real** — que é o que o Art. 10 exige
 * antes de promover qualquer coisa. Duas cópias divergiriam no primeiro acento
 * novo, e a que ficaria errada é a que ninguém abre.
 */

/**
 * A cor, que aqui **é** o conteúdo.
 *
 * Por isso a amostra leva anel de 1dp mesmo não selecionada: com menos de 24dp
 * ela precisaria dele de qualquer forma (REQ-DS-006), e aqui há um motivo a
 * mais — o quadrado é a única informação do item, sem texto ao lado para
 * recuperá-la.
 *
 * Seleção é a **espessura** do anel, não uma segunda cor: usar cor para estado
 * numa fileira de cores tornaria o sinal ilegível justamente para quem não
 * distingue as nove.
 *
 * `contentDescription` com o nome falado, senão nove quadrados anunciados como
 * "Cor" deixam quem usa leitor de tela escolhendo às cegas.
 */
@Composable
fun SeletorDeCor(selecionada: Int, onEscolher: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Acentos) { cor ->
            val argb = cor.toArgb()
            Box(
                Modifier
                    .size(AMOSTRA)
                    .clip(Formas.extraSmall)
                    .background(cor)
                    .border(
                        width = if (argb == selecionada) ESCOLHIDO else OutlineWidth,
                        color = Tema.ink,
                        shape = Formas.extraSmall,
                    )
                    .clickable { onEscolher(argb) }
                    .semantics {
                        contentDescription = NomesDeAcento[cor] ?: "Cor"
                        selected = argb == selecionada
                    },
            )
        }
    }
}

/**
 * O ícone, sobre a cor já escolhida.
 *
 * Mostrar o desenho **na cor da categoria** e não em cinza é o que faz o
 * seletor responder à pergunta real — "como isto vai ficar na lista" —, e é
 * grátis: o adesivo é o mesmo componente que a lista usa.
 *
 * Sem nome falado por ícone: a forma não tem tradução estável em palavra, e
 * "etiqueta" contra "sacola" não ajuda quem não vê nenhuma das duas. O que o
 * leitor anuncia é a posição e o estado, que é o que permite escolher.
 */
@Composable
fun SeletorDeIcone(cor: Int, selecionado: String, onEscolher: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ICONES_DE_CATEGORIA) { chave ->
            val escolhido = chave == selecionado
            Box(
                Modifier
                    .size(AMOSTRA)
                    .clip(Pill)
                    .border(
                        width = if (escolhido) ESCOLHIDO else OutlineWidth,
                        color = if (escolhido) Tema.ink else Tema.hairline,
                        shape = Pill,
                    )
                    .clickable { onEscolher(chave) }
                    .semantics {
                        contentDescription = "Ícone " + (ICONES_DE_CATEGORIA.indexOf(chave) + 1)
                        selected = escolhido
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(GLIFO_FUNDO).clip(Pill).background(Color(cor)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(
                        id = iconeDaCategoria(chave),
                        descricao = null,
                        modifier = Modifier.size(GLIFO),
                        tint = CanvasDark,
                    )
                }
            }
        }
    }
}

/** 48dp já passa do alvo mínimo sem `minimumInteractiveComponentSize`. */
private val AMOSTRA = 48.dp
private val GLIFO_FUNDO = 34.dp
private val GLIFO = 18.dp
private val ESCOLHIDO = 3.dp
