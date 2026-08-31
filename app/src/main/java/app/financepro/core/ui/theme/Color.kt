package app.financepro.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A paleta. REQ-DS-001 · [design.md](../../../../../../../../../docs/design.md) §4
 *
 * **Fonte única.** Nenhum hexadecimal de cor existe fora de `core/ui/theme/`, e
 * `TokenLintTest` reprova o build se aparecer. Uma cor solta numa tela é uma
 * cor que não inverte com o tema e não entra na conta do `ContrastTest`.
 *
 * As seis cores de sticker são **idênticas nos dois temas** (REQ-DS-008): um
 * adesivo colorido contornado funciona sobre papel claro ou escuro. O que
 * inverte é o papel e a tinta; o que não sobrevive no escuro são as três bandas
 * pastel, que ganham equivalentes dessaturados.
 */

// Papel e tinta.
val Carbon = Color(0xFF000000)
val PaperWhite = Color(0xFFFFFFFF)

// Bandas pastel — tema claro.
val SkyWash = Color(0xFFDCEEFF)
val ConcreteGray = Color(0xFFCCCCCC)
val SoftMist = Color(0xFFE9E9E9)

/**
 * Stickers. **Preenchimento, nunca cor de texto** (REQ-DS-006).
 *
 * Sobre branco só Voltage Violet passa em 4.5:1; sobre papel escuro a relação se
 * inverte e é o Violet que reprova. A regra única elimina a classe de erro em vez
 * de administrar a tabela dos dois lados.
 */
val ElectricBlue = Color(0xFF4DA2FF) // fita 3D, assinatura da marca
val MintPop = Color(0xFF55DB9C)
val Lavender = Color(0xFFE9CCFF)
val Ember = Color(0xFFFB4903)
val Sunburst = Color(0xFFFFD731)
val VoltageViolet = Color(0xFF5C4ADE)

// Papel escuro.
val CarbonPaper = Color(0xFF111111)
val SkyWashDark = Color(0xFF0D1A26)
val ConcreteDark = Color(0xFF1C1C1C)
val LavenderDark = Color(0xFF1E1729)

/** As seis, para quem precisa percorrer o conjunto — `ContrastTest`, por exemplo. */
val Stickers = listOf(ElectricBlue, MintPop, Lavender, Ember, Sunburst, VoltageViolet)

/**
 * Como cada cor se chama em voz alta. REQ-A11Y-001
 *
 * Um seletor de cores é o caso em que a cor **é** o conteúdo, e seis quadrados
 * anunciados como "Cor" deixam quem usa leitor de tela escolhendo às cegas
 * entre seis coisas idênticas.
 *
 * Nome de uso, não do token: "Azul", e não "Electric Blue". Quem ouve está
 * escolhendo a cor da conta, não lendo o guia de marca.
 *
 * Mapa e não lista paralela — índices desalinhados renomeariam cores em
 * silêncio. `TokenLintTest` prova que as seis estão aqui.
 */
val StickerNames = mapOf(
    ElectricBlue to "Azul",
    MintPop to "Verde",
    Lavender to "Lilás",
    Ember to "Laranja",
    Sunburst to "Amarelo",
    VoltageViolet to "Roxo",
)
