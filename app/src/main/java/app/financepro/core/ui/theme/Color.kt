package app.financepro.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A paleta. REQ-DS-001 · [design.md](../../../../../../../../../docs/design.md) §4
 *
 * **Fonte única.** Nenhum hexadecimal de cor existe fora de `core/ui/theme/`, e
 * `TokenLintTest` reprova o build se aparecer. Uma cor solta numa tela é uma
 * cor que não inverte com o tema e não entra na conta do `ContrastTest`.
 *
 * O sistema tem **dois modos de tela cheia** e nada entre eles: preto absoluto
 * para narrar, branco para catalogar. Cada modo tem exatamente um degrau acima
 * — [SurfaceElevated] no escuro, [SurfaceSoft] no claro. Profundidade é a troca
 * de canvas e esse degrau de luminância, nunca sombra (REQ-DS-004).
 *
 * As nove cores de acento são **idênticas nos dois temas** (REQ-DS-008), pela
 * mesma razão de sempre: uma delas é a identidade de uma categoria, e categoria
 * não muda de cor quando anoitece. O que inverte é o canvas e a tinta.
 */

// Os dois canvas. Preto absoluto, não quase-preto: `#0A0A0A` existe no sistema
// de origem para cards embutidos, e usá-lo como fundo achataria a única troca
// de banda que o desenho tem.
val CanvasDark = Color(0xFF000000)
val CanvasLight = Color(0xFFFFFFFF)

/**
 * O degrau único acima de cada canvas.
 *
 * A escada tem **dois passos e para**: preto e [SurfaceElevated] no escuro,
 * branco e [SurfaceSoft] no claro. Um terceiro tom viraria elevação tonal com
 * outro nome, que é o que REQ-DS-004 proíbe.
 */
val SurfaceElevated = Color(0xFF16181A)
val SurfaceSoft = Color(0xFFF4F4F4)

// Tinta. `InkLight` é mais quente que preto puro de propósito: texto longo em
// #000 sobre branco vibra, e o canvas escuro já gasta o preto absoluto.
val InkLight = Color(0xFF191C1F)
val MuteLight = Color(0xFF505A63)

/**
 * Branco a 72% **já achatado** sobre preto.
 *
 * Guardar a cor composta em vez de `Color.White.copy(alpha = .72f)` é o que
 * permite ao `ContrastTest` medir o que o olho vê. Alfa por cima de superfície
 * variável dá um número por fundo, e o teste passaria medindo o caso fácil.
 */
val MuteDark = Color(0xFFB8B8B8)

// Fios de 1dp. No claro é linha desenhada; no escuro é branco a 12%, que sobre
// preto vira o contorno mais fraco que ainda separa duas superfícies.
val HairlineLight = Color(0xFFE2E2E7)
val HairlineDark = Color(0x1FFFFFFF)

/**
 * Cobalto, o carimbo da marca — e no app ele quase não aparece.
 *
 * O sistema de origem gasta cobalto num card em destaque por página, e o app não
 * tem esse card: não há plano, nível nem preço aqui. Sobrou o papel que o
 * protótipo lhe deu nas telas, e é o único honesto — **cor de categoria, como as
 * outras**, e por isso ele está dentro de [Acentos] e não numa gaveta à parte.
 *
 * Já foi preenchimento do bloco de saldo, por um motivo ruim: o sistema anterior
 * tinha um bloco cheio ali e a troca de paleta o recoloriu em vez de perguntar se
 * ele ainda fazia sentido. Não fazia — a profundidade agora vem do degrau, e a
 * ênfase do saldo vem do tamanho.
 *
 * Como texto ele reprova sobre o card escuro (2.94:1), igual aos outros oito.
 */
val Cobalt = Color(0xFF494FDF)

/**
 * Acentos. **Preenchimento, nunca cor de texto** (REQ-DS-006).
 *
 * Sobre preto puro oito das nove passam em 4.5:1 — e é exatamente aí que a
 * regra seria perdida por descuido, porque *parece* que dá para usar quase
 * qualquer uma como texto. Sobre o card [SurfaceElevated], que é onde o conteúdo
 * real vive, **seis** reprovam: Cobalto 2.94, Light Blue 3.91, Brown 3.90, Pink
 * 3.94, Light Green 3.95 e Danger 4.20.
 *
 * A regra única elimina a classe de erro em vez de administrar a tabela por
 * superfície. `ContrastTest` mede os dois fundos e guarda esse flanco.
 */
val Teal = Color(0xFF00A87E)
val LightBlue = Color(0xFF007BC2)
val LightGreen = Color(0xFF428619)
val Yellow = Color(0xFFB09000)
val Warning = Color(0xFFEC7E00)
val Pink = Color(0xFFE61E49)
val Danger = Color(0xFFE23B4A)
val Brown = Color(0xFF936D62)

/** As nove, para quem precisa percorrer o conjunto — `ContrastTest`, por exemplo. */
val Acentos = listOf(Teal, LightBlue, LightGreen, Yellow, Warning, Pink, Danger, Brown, Cobalt)

/**
 * Como cada cor se chama em voz alta. REQ-A11Y-001
 *
 * Um seletor de cores é o caso em que a cor **é** o conteúdo, e oito quadrados
 * anunciados como "Cor" deixam quem usa leitor de tela escolhendo às cegas
 * entre oito coisas idênticas.
 *
 * Nome de uso, não do token: "Verde", e não "Light Green". Quem ouve está
 * escolhendo a cor da categoria, não lendo o guia de marca.
 *
 * Mapa e não lista paralela — índices desalinhados renomeariam cores em
 * silêncio. `TokenLintTest` prova que as nove estão aqui.
 */
val NomesDeAcento = mapOf(
    Teal to "Verde-azulado",
    LightBlue to "Azul",
    LightGreen to "Verde",
    Yellow to "Amarelo",
    Warning to "Laranja",
    Pink to "Rosa",
    Danger to "Vermelho",
    Brown to "Marrom",
    Cobalt to "Violeta",
)
