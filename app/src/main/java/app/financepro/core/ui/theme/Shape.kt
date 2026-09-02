package app.financepro.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas. REQ-DS-003 · [design.md](../../../../../../../../../docs/design.md) §4.1
 *
 * A escala tem quatro degraus e uma pílula, e cada degrau tem dono:
 *
 * | Raio | Onde |
 * |---|---|
 * | 8dp | tag inline, chip pequeno |
 * | 12dp | campo de texto, tile |
 * | 20dp | card e folha |
 * | 28dp | chrome de dispositivo, bottom sheet |
 *
 * **Botão é sempre pílula, card é sempre 20dp.** Não é preferência: a diferença
 * entre a ação e o conteúdo passa a ser a forma, e não a cor — o que sobrevive
 * ao daltonismo e à troca de canvas sem nenhuma condicional (REQ-A11Y-003).
 *
 * Nada de raio intermediário. Um card de 16dp no meio da tela lê como
 * componente de outro app, e o revisor não tem régua para discutir 16 contra 20.
 */
val SlushShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), // tag inline, chip pequeno
    small = RoundedCornerShape(12.dp), // campo, tile
    medium = RoundedCornerShape(20.dp), // card
    large = RoundedCornerShape(20.dp), // card e folha
    extraLarge = RoundedCornerShape(28.dp), // folha de fundo, chrome
)

/** Nav, botões, chips e badges. Tudo que é ação. */
val Pill = CircleShape

/**
 * Espessura do fio entre superfícies de mesmo tom (REQ-DS-002).
 *
 * Continua 1dp, mas mudou de papel: era a moldura de toda superfície, agora é o
 * separador de exceção. Onde há degrau de luminância — card sobre canvas — não
 * se desenha fio nenhum.
 */
val OutlineWidth = 1.dp
