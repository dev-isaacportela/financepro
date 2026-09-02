package app.financepro.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.financepro.R
import app.financepro.core.ui.theme.CanvasDark
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Tema

/**
 * Ícones. REQ-A11Y-001 ·
 * [design.md](../../../../../../../../../docs/design.md) §6.5
 *
 * **Vetores em `res/drawable/`, não biblioteca.** `material-icons-extended`
 * traria alguns milhares de desenhos para o app usar vinte e dois, e este
 * projeto já recusa dependência por menos que isso. Os traços são os mesmos do
 * protótipo em `docs/preview/` — o `pathData` veio de lá, não de uma segunda
 * fonte que divergiria na primeira revisão.
 *
 * Todos são **traço**, nunca preenchimento chapado: um ícone preenchido de 20dp
 * sobre o card vira mancha, e a barra de navegação ficaria pesada exatamente na
 * parte da tela que se olha de relance.
 *
 * A tinta padrão é `LocalContentColor`, e não `Tema.ink`: dentro do botão
 * primário o conteúdo é `paper`, e um padrão fixo em `ink` desenhava o glifo
 * branco sobre a pílula branca. O ícone somia sem erro nenhum no código.
 */
@Composable
fun Icone(
    @DrawableRes id: Int,
    descricao: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = descricao,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * O desenho de uma categoria, a partir da `iconKey` que ela já carrega.
 *
 * O `when` é exaustivo com um `else` de propósito: a chave vem do banco, e uma
 * categoria criada por importação ou restauração pode trazer chave desconhecida.
 * Cair num círculo neutro é melhor que quebrar a lista.
 */
@DrawableRes
fun iconeDaCategoria(iconKey: String): Int = when (iconKey) {
    "utensils" -> R.drawable.ic_cat_utensils
    "car" -> R.drawable.ic_cat_car
    "home" -> R.drawable.ic_cat_home
    "cross" -> R.drawable.ic_cat_cross
    "confetti" -> R.drawable.ic_cat_confetti
    "book" -> R.drawable.ic_cat_book
    "bag" -> R.drawable.ic_cat_bag
    "repeat" -> R.drawable.ic_cat_repeat
    "cash" -> R.drawable.ic_cat_cash
    "dots" -> R.drawable.ic_cat_dots
    "tag" -> R.drawable.ic_cat_tag
    else -> R.drawable.ic_cat_generico
}

/**
 * As chaves que o seletor oferece, na ordem em que aparecem.
 *
 * Lista e não `iconeDaCategoria.keys`: o mapeamento aceita chave desconhecida de
 * propósito (importação, restauração), e oferecer o genérico como escolha seria
 * deixar alguém pegar "sem ícone" achando que é um ícone.
 */
val ICONES_DE_CATEGORIA = listOf(
    "utensils", "car", "home", "cross", "confetti",
    "book", "bag", "repeat", "cash", "tag", "dots",
)

/**
 * O adesivo redondo da categoria: preenchimento de acento com o ícone dentro.
 *
 * **O ícone é sempre [CanvasDark], e nunca branco.** É a divergência consciente
 * em relação ao protótipo, que desenhou glifos brancos sobre os círculos: branco
 * sobre Laranja dá 2.78:1, abaixo dos 3:1 de elemento não textual da WCAG. Preto
 * passa sobre os nove acentos — o pior caso é Cobalto, com 3.47:1 — e resolve
 * com **uma** regra em vez de uma tabela de tinta por cor.
 *
 * A cor não muda com o tema, então a tinta de cima também não muda: um `when`
 * por tema aqui produziria um adesivo que troca de cara ao anoitecer, que é
 * justamente o que REQ-DS-008 evita.
 *
 * **Sem anel.** REQ-DS-006 exige o contorno de 1dp em preenchimento de acento
 * com menos de 24dp; este tem 36 e fica de fora da regra — e não é folga
 * arbitrária: o glifo escuro por dentro já dá ao círculo uma borda de contraste
 * que um ponto de 10dp não tem, então a forma se lê mesmo quando a cor encosta
 * no tom da superfície.
 *
 * A primeira versão levava o anel assim mesmo, por hábito da gramática anterior.
 * Sobre preto, oito anéis brancos numa lista pesam mais que os oito ícones.
 *
 * Sem semântica: o nome da categoria vem escrito na linha, e um
 * `contentDescription` aqui faria o leitor de tela anunciar a categoria duas
 * vezes seguidas.
 */
@Composable
fun AvatarDeCategoria(
    colorArgb: Int?,
    iconKey: String?,
    modifier: Modifier = Modifier,
    tamanho: androidx.compose.ui.unit.Dp = AVATAR,
) {
    val cor = colorArgb?.let { Color(it) } ?: Tema.inkMute
    Box(
        modifier = modifier
            .size(tamanho)
            .clip(Pill)
            .background(cor)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icone(
            id = iconeDaCategoria(iconKey.orEmpty()),
            descricao = null,
            modifier = Modifier.size(tamanho * GLIFO),
            tint = CanvasDark,
        )
    }
}

/** 36dp é o círculo do protótipo, e já passa do alvo mínimo quando é tocável. */
private val AVATAR = 36.dp

/** O glifo ocupa metade do círculo; mais que isso encosta no anel. */
private const val GLIFO = 0.5f
