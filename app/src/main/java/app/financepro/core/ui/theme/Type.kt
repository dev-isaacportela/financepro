// `FontVariation` ainda é experimental no Compose. O opt-in é deliberado: é o
// que permite tirar três pesos de Inter de um arquivo só, em vez de empacotar
// três estáticos. Se a API mudar, muda aqui, num arquivo.
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
 * **Uma família só, três pesos.** O sistema de origem usa uma display
 * proprietária que não é licenciável aqui, e ele próprio nomeia o substituto:
 * Inter, com entrelinha travada em `1.0` e entreletra negativa nos tamanhos
 * grandes. Trocar de família no display renderia menos que acertar essas duas
 * medidas, e custaria um arquivo de fonte a mais no APK.
 *
 * Inter é variável, um arquivo por família. `FontVariation.weight` escolhe o
 * eixo `wght` em tempo de execução (API 26+, que é o `minSdk`).
 */

// ponytail: Inter variável completa, 876KB. Subsetar para latin + latin-ext
// derruba para ~120KB (design.md §3) — exige fontTools, que não instala nesta
// máquina. Fazer quando a ferramenta estiver disponível, ou no CI.
private val Inter = FontFamily(
    Font(
        R.font.inter,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_REGULAR)),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.W500,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_MEDIUM)),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_SEMIBOLD)),
    ),
)

private const val WEIGHT_REGULAR = 400
private const val WEIGHT_MEDIUM = 500
private const val WEIGHT_SEMIBOLD = 600

/**
 * Os dois ajustes sem os quais a entrelinha travada **não aparece na tela**.
 *
 * O Compose acrescenta a folga de métrica da fonte acima e abaixo de cada linha;
 * com ela, um `lineHeight` de `1.0em` rende o espaçamento de um parágrafo comum.
 * O empilhamento apertado — a única razão de a regra existir — se perde em
 * silêncio, com o código parecendo correto. É o erro que se comete uma vez e
 * demora a diagnosticar, e por isso `TypographyTest` o vigia.
 *
 * O mecanismo é o mesmo de antes; o que mudou foi o número que ele entrega.
 */
private val CrushedLeading = PlatformTextStyle(includeFontPadding = false)
private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

/**
 * Display: peso 500, entrelinha `1.0em`, entreletra negativa que **cresce com o
 * tamanho**. Um título de 64sp com o tracking de um corpo de texto lê como
 * banner de anúncio; é o ajuste que separa tipo grande de tipo apenas ampliado.
 */
private fun display(size: TextUnit, tracking: Float) = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W500,
    fontSize = size,
    lineHeight = LEADING_DISPLAY.em,
    letterSpacing = tracking.em,
    platformStyle = CrushedLeading,
    lineHeightStyle = TrimBoth,
)

/** Travada em 1.0 por REQ-DS-005. Afrouxar aqui desmonta o empilhamento. */
private const val LEADING_DISPLAY = 1.0f

val DisplayXl = display(64.sp, -0.020f) // onboarding
val Display = display(44.sp, -0.015f) // estados vazios, banners de seção
val DisplaySm = display(34.sp, -0.010f) // saldo, total da fatura

/** Os três, para `TypographyTest` não depender de alguém lembrar de listá-los. */
val DisplayStyles = listOf(DisplayXl, Display, DisplaySm)

/**
 * Algarismos tabulares. Sem eles os valores não alinham na vertical, e uma coluna
 * de dinheiro desalinhada é mais difícil de conferir contra o extrato do banco.
 */
private const val TNUM = "tnum"

val HeadingSm = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W500, fontSize = 28.sp, lineHeight = 1.19.em, letterSpacing = (-0.01).em)
val Subheading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W500, fontSize = 22.sp, lineHeight = 1.33.em)

/**
 * Corpo com entreletra **positiva**.
 *
 * É o detalhe que dá a precisão mecânica que o sistema pede: a mesma frase com
 * tracking zero lê como texto de artigo, e com `+0.015em` lê como rótulo de
 * interface. Vale só para o corpo e os rótulos — no display o sinal se inverte.
 */
val BodyLg = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W400,
    fontSize = 18.sp,
    lineHeight = 1.56.em,
    letterSpacing = 0.005.em,
)
val Body = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W400,
    fontSize = 16.sp,
    lineHeight = 1.5.em,
    letterSpacing = 0.015.em,
)
val Caption = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W400,
    fontSize = 13.sp,
    lineHeight = 1.4.em,
)

/** A descrição da linha de transação, que design.md §6.3 pede em peso 500. */
val BodyStrong = Body.copy(fontWeight = FontWeight.W500)

/**
 * O rótulo da barra de navegação, e o mesmo em peso 600.
 *
 * 11sp, e não `Caption`: "Transações" é a mais longa das quatro e, a 13sp, não
 * cabe na quarta parte de 360dp — saía com reticências. Diminuir a fonte é
 * melhor que abreviar a palavra, que é o que o usuário lê para saber onde está.
 */
val NavRotulo = Caption.copy(fontSize = 11.sp)
val NavRotuloForte = NavRotulo.copy(fontWeight = FontWeight.W600)

/**
 * A legenda em peso 600.
 *
 * Existe por acessibilidade, não por estética: é o segundo canal da aba
 * selecionada na barra de navegação, ao lado da tinta. Peso não é cor, e é o que
 * sobra para quem não distingue `ink` de `inkMute` (REQ-A11Y-003).
 */
val CaptionForte = Caption.copy(fontWeight = FontWeight.W600)

/** Nav, botões e rótulos. Peso 600 — o sistema não usa o 500 aqui, e nem o 700. */
val Label = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.W600,
    fontSize = 14.sp,
    lineHeight = 1.43.em,
)

// Todo valor monetário passa por um destes dois, via `MoneyText` (REQ-DS-007).
val MoneyLg = DisplaySm.copy(fontFeatureSettings = TNUM)
val MoneyBody = Body.copy(fontWeight = FontWeight.W600, letterSpacing = 0.em, fontFeatureSettings = TNUM)

/** Saldo corrente do extrato: `tnum` também aqui, senão a coluna não alinha. */
val MoneyCaption = Caption.copy(fontFeatureSettings = TNUM)

/**
 * O mapa para o Material 3, para que um `Text` sem `style` explícito já caia num
 * estilo do tema em vez do Roboto padrão.
 */
val Tipografia = Typography(
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
