package app.financepro.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas. REQ-DS-003 · [design.md](../../../../../../../../../docs/design.md) §4.1
 *
 * Slush é web em px; aqui é dp, e os números são mantidos. O raio de `1600px` da
 * referência não era um raio — era a forma de dizer "pílula", e vira
 * [Pill].
 *
 * **Nada abaixo de 16dp.** É regra, não preferência: o canto muito arredondado é
 * metade da gramática do sticker, e um card de 8dp no meio da tela lê como
 * componente de outro app.
 */
val SlushShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp), // sticker pequeno, ícone de carteira
    small = RoundedCornerShape(20.dp), // cards
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(30.dp), // corpo, folhas
    extraLarge = RoundedCornerShape(40.dp), // cards elevados, bottom sheet
)

/** Nav, botões, chips e tags. */
val Pill = CircleShape

/** Espessura do contorno `ink` de toda superfície (REQ-DS-002). */
val OutlineWidth = 1.dp
