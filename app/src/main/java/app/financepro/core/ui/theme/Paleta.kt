package app.financepro.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Os tokens de um tema. REQ-DS-002 · REQ-DS-008
 *
 * [paper] é o canvas do modo e [surface] o único degrau acima dele. Os dois
 * juntos são a linguagem de profundidade inteira: um card não tem sombra nem
 * contorno pesado, ele é a superfície mais clara (ou mais escura) que o fundo.
 *
 * [hairline] existe para o caso em que duas superfícies do mesmo tom se
 * encostam — lista dentro de card, campo dentro de folha. É fio de 1dp, não
 * moldura: no escuro é branco a 12%, e some assim que houver diferença de
 * luminância para fazer o trabalho.
 *
 * As nove cores de acento não aparecem aqui de propósito: elas são idênticas
 * nos dois temas (REQ-DS-008) e vivem soltas em `Color.kt`. O que muda por tema
 * é o canvas, o degrau, a tinta e o fio.
 */
@Immutable
data class Paleta(
    val paper: Color, // canvas do modo — preto absoluto ou branco
    val surface: Color, // o degrau único acima do canvas
    val ink: Color, // texto primário
    val inkMute: Color, // texto secundário; passa em 4.5:1 sobre paper e surface
    val hairline: Color, // fio de 1dp entre superfícies de mesmo tom
)

/**
 * O modo escuro é o principal, e não uma variação do claro.
 *
 * A ordem das declarações registra isso: o app de finanças passa a maior parte
 * do tempo mostrando números sobre preto, e o modo claro é a banda de catálogo
 * — ajustes, listas longas de cadastro, formulários.
 */
val PaletaEscura = Paleta(
    paper = CanvasDark,
    surface = SurfaceElevated,
    ink = CanvasLight,
    inkMute = MuteDark,
    hairline = HairlineDark,
)

val PaletaClara = Paleta(
    paper = CanvasLight,
    surface = SurfaceSoft,
    ink = InkLight,
    inkMute = MuteLight,
    hairline = HairlineLight,
)
