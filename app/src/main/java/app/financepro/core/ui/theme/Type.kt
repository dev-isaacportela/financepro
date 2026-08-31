// `FontVariation` ainda é experimental no Compose. O opt-in é deliberado: é o
// que permite tirar dois pesos de Inter de um arquivo só, em vez de empacotar
// dois estáticos. Se a API mudar, muda aqui, num arquivo.
@file:OptIn(ExperimentalTextApi::class)

package app.financepro.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.financepro.R

/**
 * Tipografia. REQ-DS-005 · REQ-DS-007 · REQ-DS-010 ·
 * [design.md](../../../../../../../../../docs/design.md) §3 e §4.2
 *
 * Fontes **empacotadas** em `res/font/`, nunca Downloadable Fonts: elas exigem
 * rede e Google Play Services, e seriam a primeira coisa a furar a garantia de
 * REQ-SEC-007 sem ninguém perceber. `ManifestTest` guarda esse flanco.
 *
 * Antonio e Inter são variáveis, um arquivo por família. `FontVariation.weight`
 * escolhe o eixo `wght` em tempo de execução (API 26+, que é o `minSdk`) — dois
 * estáticos de Inter custariam o dobro de APK pelo mesmo resultado.
 */

private val Antonio = FontFamily(
    Font(
        R.font.antonio,
        weight = FontWeight.W700,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_BOLD)),
    ),
)

// ponytail: Inter variável completa, 876KB. Subsetar para latin + latin-ext
// derruba para ~120KB (design.md §3) — exige fontTools, que não instala nesta
// máquina. Fazer quando a ferramenta estiver disponível, ou no CI.
private val Inter = FontFamily(
    Font(
        R.font.inter,
        weight = FontWeight.W500,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_MEDIUM)),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.W700,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_BOLD)),
    ),
)

private const val WEIGHT_MEDIUM = 500
private const val WEIGHT_BOLD = 700

/**
 * Os dois ajustes sem os quais o `lineHeight` esmagado **não aparece na tela**.
 *
 * O Compose acrescenta a folga de métrica da fonte acima e abaixo de cada linha;
 * com ela, um `lineHeight` de 0.78em rende o espaçamento de um parágrafo comum. O
 * efeito escultural — a única razão de a regra existir — se perde em silêncio, com
 * o código parecendo correto. É o erro que se comete uma vez e demora a
 * diagnosticar, e por isso `TypographyTest` o vigia.
 */
private val CrushedLeading = PlatformTextStyle(includeFontPadding = false)
private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun display(size: TextUnit, leading: Float) = TextStyle(
    fontFamily = Antonio,
    fontWeight = FontWeight.W700,
    fontSize = size,
    lineHeight = leading.em,
    platformStyle = CrushedLeading,
    lineHeightStyle = TrimBoth,
)

val DisplayXl = display(88.sp, 0.78f) // onboarding
val Display = display(64.sp, 0.80f) // estados vazios, banners de seção
val DisplaySm = display(44.sp, 0.82f) // saldo, total da fatura

/** Os três, para `TypographyTest` não depender de alguém lembrar de listá-los. */
val DisplayStyles = listOf(DisplayXl, Display, DisplaySm)

/**
 * Algarismos tabulares. Sem eles os valores não alinham na vertical, e uma coluna
 * de dinheiro desalinhada é mais difícil de conferir contra o extrato do banco.
 */
private const val TNUM = "tnum"

val HeadingSm = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W700, fontSize = 30.sp, lineHeight = 1.1.em)
val Subheading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W700, fontSize = 24.sp, lineHeight = 1.2.em)
val BodyLg = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W500,
    fontSize = 15.sp,
    lineHeight = 1.39.em,
    letterSpacing = (-0.01).em,
)
val Body = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W500,
    fontSize = 14.sp,
    letterSpacing = (-0.01).em,
)
val Caption = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W500,
    fontSize = 12.sp,
    lineHeight = 1.56.em,
    letterSpacing = (-0.01).em,
)

/** A descrição da linha de transação, que design.md §6.3 pede em peso 700. */
val BodyStrong = Body.copy(fontWeight = FontWeight.W700)

/** Nav, botões e rótulos: a abertura é o que dá ar aos controles pill. */
val Label = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W700,
    fontSize = 13.sp,
    letterSpacing = 0.032.em,
)

// Todo valor monetário passa por um destes dois, via `MoneyText` (REQ-DS-007).
val MoneyLg = DisplaySm.copy(fontFeatureSettings = TNUM)
val MoneyBody = Body.copy(fontWeight = FontWeight.W700, fontSize = 15.sp, fontFeatureSettings = TNUM)

/** Saldo corrente do extrato: `tnum` também aqui, senão a coluna não alinha. */
val MoneyCaption = Caption.copy(fontFeatureSettings = TNUM)

/**
 * O mapa para o Material 3, para que um `Text` sem `style` explícito já caia num
 * estilo Slush em vez do Roboto padrão.
 */
val SlushTypography = Typography(
    displayLarge = DisplayXl,
    displayMedium = Display,
    displaySmall = DisplaySm,
    headlineMedium = HeadingSm,
    headlineSmall = Subheading,
    bodyLarge = BodyLg,
    bodyMedium = Body,
    bodySmall = Caption,
    labelLarge = Label,
    labelMedium = Label,
    labelSmall = Caption,
)
