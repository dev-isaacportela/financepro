package app.financepro.feature

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * Movimento entre telas. REQ-UI-001 ·
 * [design.md](../../../../../../../../docs/design.md) §6.4
 *
 * O padrão do `NavHost` é um fade, e fade é a ausência de decisão: não diz de
 * onde a tela nova veio nem para onde a anterior foi. Num app de quatro abas a
 * direção **existe** — "Orçamento" fica à direita de "Transações", e "Contas"
 * fica dentro de "Mais" —, e é ela que o movimento tem de mostrar.
 *
 * Três opções, uma linha para trocar ([MOVIMENTO]). Todas são de espaço, não de
 * opacidade, e nenhuma usa sombra ou gradiente (REQ-DS-004):
 *
 * - [Movimento.Papel] — a tela nova entra por inteiro pelo lado e a que sai anda
 *   um quarto no mesmo sentido. O parallax é o que separa isto de um slide seco.
 *   **Padrão**, escolhido no aparelho contra os outros dois.
 * - [Movimento.Sticker] — mola com overshoot: a nova chega a 94% e assenta, a
 *   antiga recua para 96%. É a gramática do adesivo, e a mais viva.
 * - [Movimento.Folha] — avançar sobe a tela nova de baixo, voltar derruba a de
 *   cima. Dá profundidade de pilha; a mais discreta das três.
 *
 * A escala de animação do sistema continua valendo: quem desliga animações nas
 * opções de acessibilidade recebe as telas sem transição, sem nada aqui saber
 * disso — o Compose lê a preferência no recompositor.
 */
enum class Movimento { Papel, Sticker, Folha }

/**
 * O movimento em vigor, e uma linha para trocá-lo.
 *
 * **Não é preferência do usuário.** O seletor no "Mais" existiu só enquanto a
 * escolha estava aberta — comparar os três no aparelho é trabalho de quem
 * desenha, e um ajuste a mais numa tela de finanças é uma pergunta que o app faz
 * a quem só queria lançar uma despesa. Escolhido o Papel, o seletor saiu e os
 * outros dois ficam aqui, a um `val` de distância, para a próxima revisão.
 */
val MOVIMENTO = Movimento.Papel

// Os números do movimento em um lugar só: são o que se ajusta quando a
// transição "parece lenta" ou "parece nervosa", e caçá-los dentro de cinco
// lambdas é o que faz ninguém ajustar.
private const val MS = 320
private const val PARALLAX = 4 // fração da largura que a tela de saída percorre
private const val EMPURRAO = 6 // idem, no preset de mola: quase no lugar, só o empurrão
private const val ESCALA_ENTRA = 0.94f
private const val ESCALA_SAI = 0.96f
private const val ESCALA_VOLTA = 1.04f
private const val AMORTECIMENTO = 0.62f // < 1 é o que produz o overshoot do sticker
private const val AMORTECIMENTO_PILULA = 0.8f // a pílula da barra anda pouco: quase sem overshoot

private val Desliza = tween<IntOffset>(MS, easing = FastOutSlowInEasing)
private val Encolhe = tween<Float>(MS, easing = FastOutSlowInEasing)
private val Mola: FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = AMORTECIMENTO,
    stiffness = Spring.StiffnessMediumLow,
    // Sem isto a mola de IntOffset persegue frações de pixel por centenas de ms:
    // o olho vê a tela parada e o app continua animando.
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
private val MolaEscala: FiniteAnimationSpec<Float> =
    spring(dampingRatio = AMORTECIMENTO, stiffness = Spring.StiffnessMediumLow)

/**
 * A mola da pílula da barra inferior (`BarraInferior`, em Nav.kt). Mais
 * amortecida que a das telas: a pílula percorre uma distância curta, e passar do
 * alvo numa distância dessas lê como imprecisão, não como elasticidade.
 */
internal val MolaPilula: FiniteAnimationSpec<Float> =
    spring(dampingRatio = AMORTECIMENTO_PILULA, stiffness = Spring.StiffnessMediumLow)

/**
 * A ordem das abas **é** a ordem espacial da barra, e destino de dentro do
 * "Mais" é mais fundo que qualquer aba. É o que dá o sentido do deslize sem
 * perguntar ao Navigation se foi `navigate` ou `popBackStack` — a troca de aba
 * é um pop por dentro (`popUpTo(Inicio)`), então "voltar" mentiria sobre a
 * direção em metade dos casos.
 */
private val ORDEM = listOf(Inicio, Transacoes(), Orcamento, Mais)

private fun NavBackStackEntry.profundidade(): Int {
    val i = ORDEM.indexOfFirst { destination.hasRoute(it::class) }
    return if (i < 0) ORDEM.size else i
}

/** +1 quando se vai para a direita ou para dentro; −1 quando se volta. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.sentido(): Int =
    if (targetState.profundidade() >= initialState.profundidade()) 1 else -1

fun AnimatedContentTransitionScope<NavBackStackEntry>.entradaSlush(): EnterTransition {
    val d = sentido()
    return when (MOVIMENTO) {
        Movimento.Papel -> slideIn(Desliza) { IntOffset(d * it.width, 0) }

        Movimento.Sticker -> slideIn(Mola) { IntOffset(d * it.width / EMPURRAO, 0) } +
            scaleIn(MolaEscala, initialScale = ESCALA_ENTRA)

        Movimento.Folha ->
            if (d > 0) slideIn(Desliza) { IntOffset(0, it.height) }
            else scaleIn(Encolhe, initialScale = ESCALA_VOLTA)
    }
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.saidaSlush(): ExitTransition {
    val d = sentido()
    return when (MOVIMENTO) {
        Movimento.Papel -> slideOut(Desliza) { IntOffset(-d * it.width / PARALLAX, 0) }

        Movimento.Sticker -> slideOut(Desliza) { IntOffset(-d * it.width / EMPURRAO, 0) } +
            scaleOut(Encolhe, targetScale = ESCALA_SAI)

        Movimento.Folha ->
            if (d > 0) scaleOut(Encolhe, targetScale = ESCALA_ENTRA)
            else slideOut(Desliza) { IntOffset(0, it.height) }
    }
}
