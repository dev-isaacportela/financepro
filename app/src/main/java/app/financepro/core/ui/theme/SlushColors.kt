package app.financepro.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Os tokens de um tema. REQ-DS-002 · REQ-DS-008
 *
 * [ink] é ao mesmo tempo a cor do texto e a do contorno. É o que faz o contorno
 * inverter junto com o tema **sem nenhuma condicional espalhada pelas telas** —
 * nenhuma tela pergunta se está no escuro.
 *
 * As seis cores de sticker não aparecem aqui de propósito: elas são idênticas
 * nos dois temas (REQ-DS-008) e vivem soltas em `Color.kt`. O que muda por tema
 * é o papel, a tinta e as três bandas.
 */
@Immutable
data class SlushColors(
    val paper: Color, // fundo da superfície
    val ink: Color, // texto e contorno
    val bandSky: Color,
    val bandNeutral: Color,
    val bandLavender: Color,
    val onFill: Color, // texto sobre preenchimento saturado
)

val LightSlush = SlushColors(
    paper = PaperWhite,
    ink = Carbon,
    bandSky = SkyWash,
    bandNeutral = ConcreteGray,
    bandLavender = Lavender,
    onFill = PaperWhite,
)

val DarkSlush = SlushColors(
    paper = CarbonPaper,
    ink = PaperWhite,
    bandSky = SkyWashDark,
    bandNeutral = ConcreteDark,
    bandLavender = LavenderDark,
    onFill = PaperWhite,
)
