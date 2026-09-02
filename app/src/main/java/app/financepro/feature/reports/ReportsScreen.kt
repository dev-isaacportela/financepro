package app.financepro.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.LinhaDeTransacao
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.usecase.GrupoDeCategoria
import app.financepro.domain.usecase.PontoMensal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Os três relatórios. REQ-RPT-001 · REQ-RPT-002 · REQ-RPT-003 · REQ-RPT-004
 *
 * **Desenhados à mão, sem biblioteca de gráficos.** A pizza é um `drawArc` por
 * fatia e a evolução são dois `Path` — juntos, menos de sessenta linhas. Uma
 * dependência de gráficos traria tema próprio para brigar com o de Slush
 * (REQ-DS-004 proíbe superfície tonal, e o contorno é a gramática da casa), e
 * a que o catálogo já nomeava nem desenha pizza: seria dependência nova para
 * metade do trabalho.
 *
 * As cores das fatias são as **das categorias**, que já existem desde o grid do
 * lançamento rápido — inventar uma paleta de gráfico aqui daria à mesma
 * categoria duas cores no mesmo app. E nenhuma informação depende só delas: a
 * legenda traz nome, valor e percentual escritos (REQ-A11Y-003).
 */
@Composable
fun ReportsScreen(
    onVerCategoria: (Long, YearMonth) -> Unit,
    onEditar: (Long) -> Unit,
    vm: ReportsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cabecalho(mes = state.mes, onAnterior = vm::mesAnterior, onSeguinte = vm::mesSeguinte)

        if (state.vazio) {
            // REQ-UI-006 — e aqui o vazio é informação: não houve despesa no mês.
            Text("Nenhuma despesa neste mês.", style = Body, color = Slush.ink)
        } else {
            Pizza(state, onVerCategoria)
            Maiores(state, onEditar)
        }

        Evolucao(state.evolucao)
    }
}

/** Mesma gramática do cabeçalho da lista e do orçamento: o mês em linha própria. */
@Composable
private fun Cabecalho(mes: YearMonth, onAnterior: () -> Unit, onSeguinte: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = MES.format(mes).replaceFirstChar { it.uppercase() },
            style = Subheading,
            color = Slush.ink,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(text = "◀", onClick = onAnterior)
            GhostButton(text = "▶", onClick = onSeguinte)
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * REQ-RPT-001 e REQ-RPT-004 — a pizza, e o toque que leva à lista filtrada.
 *
 * A fatia é tocável **e** a linha da legenda também. A fatia porque é o que o
 * requisito diz; a legenda porque uma cunha fina não tem 48dp em lugar nenhum
 * (REQ-A11Y-002) e o leitor de tela não alcança um pedaço de `Canvas`. Sem a
 * segunda, o detalhamento seria inacessível para quem mais precisa dele.
 */
@Composable
private fun Pizza(state: ReportsState, onVerCategoria: (Long, YearMonth) -> Unit) {
    val fatias = state.fatias
    val total = state.totalCents
    val varreduras = fatias.map { GRAUS * it.totalCents / total }
    val tinta = Slush.ink
    val papel = Slush.paper

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Despesas por categoria", style = Subheading, color = Slush.ink)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTURA_PIZZA)
                .pointerInput(varreduras) {
                    detectTapGestures { toque ->
                        fatiaTocada(toque, size, varreduras)
                            ?.let { fatias[it].categoriaId }
                            ?.let { onVerCategoria(it, state.mes) }
                    }
                },
        ) {
            desenharPizza(varreduras, fatias.map { corDe(state, it, papel) }, tinta)
        }

        fatias.forEach { fatia ->
            LinhaDaLegenda(
                fatia = fatia,
                nome = state.categoriaDe(fatia.categoriaId)?.name ?: "Sem categoria",
                cor = corDe(state, fatia, papel),
                percentual = percentual(fatia.totalCents, total),
                onClick = fatia.categoriaId?.let { { onVerCategoria(it, state.mes) } },
            )
        }
    }
}

/**
 * Uma linha da legenda. O ponto de cor é o mesmo da lista de transações, com
 * anel pela mesma razão: um Laranja sobre a superfície clara dá 2.53:1 e some.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinhaDaLegenda(
    fatia: GrupoDeCategoria,
    nome: String,
    cor: Color,
    percentual: Int,
    onClick: (() -> Unit)?,
) {
    val toque = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClickLabel = "Ver transações", onClick = onClick)
    }

    SlushCard(Modifier.fillMaxWidth().then(toque)) {
        FlowRow(
            Modifier
                .padding(12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$nome, ${formatBRL(fatia.totalCents)}, $percentual%"
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(PONTO)
                    .clip(Pill)
                    .background(cor)
                    .border(OutlineWidth, Slush.ink, Pill),
            )
            Text(nome, style = BodyStrong, color = Slush.ink, modifier = Modifier.weight(1f))
            MoneyText(cents = fatia.totalCents, style = MoneyCaption)
            Text("$percentual%", style = Caption, color = Slush.ink)
        }
    }
}

/**
 * REQ-RPT-002 — doze períodos, receitas e despesas.
 *
 * Linha cheia para receita, tracejada para despesa, **as duas em tinta**: cor
 * nunca é o único sinal (REQ-A11Y-003), e verde para entrada e vermelho para
 * saída é exatamente o que REQ-DS-007 proíbe. O rótulo diz o teto da escala,
 * sem o qual duas linhas sem números não dizem de quanto se está falando.
 */
@Composable
private fun Evolucao(pontos: List<PontoMensal>) {
    if (pontos.size < 2) return
    val tinta = Slush.ink
    val teto = pontos.maxOf { max(it.receitasCents, -it.despesasCents) }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Últimos 12 meses", style = Subheading, color = Slush.ink)
        Canvas(Modifier.fillMaxWidth().height(ALTURA_LINHA)) {
            desenharLinha(pontos.map { it.receitasCents }, teto, tinta, tracejada = false)
            desenharLinha(pontos.map { -it.despesasCents }, teto, tinta, tracejada = true)
        }
        Text(
            text = "— Receitas · - - Despesas · topo da escala " + formatBRL(teto),
            style = Caption,
            color = Slush.ink,
        )
        Text(
            text = MES_CURTO.format(pontos.first().mes) + " a " + MES_CURTO.format(pontos.last().mes),
            style = Caption,
            color = Slush.ink,
        )
    }
}

/** REQ-RPT-003 — as dez maiores, na mesma linha da lista de transações. */
@Composable
private fun Maiores(state: ReportsState, onEditar: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Maiores despesas", style = Subheading, color = Slush.ink)
        state.maiores.forEach { txn ->
            LinhaDeTransacao(
                txn = txn,
                categoria = state.categoriaDe(txn.categoryId),
                conta = null,
                destino = null,
                onClick = { onEditar(txn.id) },
            )
        }
    }
}

/**
 * A pizza começa no topo e anda no sentido do relógio, como todo mundo desenha.
 * O contorno vem depois do preenchimento, e por fatia: sem ele, duas categorias
 * de cor parecida viram uma mancha só.
 */
private fun DrawScope.desenharPizza(varreduras: List<Float>, cores: List<Color>, tinta: Color) {
    val lado = size.minDimension
    val canto = Offset((size.width - lado) / 2f, (size.height - lado) / 2f)
    val quadrado = Size(lado, lado)
    var inicio = TOPO
    varreduras.forEachIndexed { i, varredura ->
        drawArc(cores[i], inicio, varredura, useCenter = true, topLeft = canto, size = quadrado)
        drawArc(
            color = tinta,
            startAngle = inicio,
            sweepAngle = varredura,
            useCenter = true,
            topLeft = canto,
            size = quadrado,
            style = Stroke(width = OutlineWidth.toPx()),
        )
        inicio += varredura
    }
}

private fun DrawScope.desenharLinha(valores: List<Long>, teto: Long, tinta: Color, tracejada: Boolean) {
    val passo = size.width / (valores.size - 1)
    val caminho = Path()
    valores.forEachIndexed { i, valor ->
        val x = passo * i
        val y = size.height * (1f - valor.toFloat() / teto)
        if (i == 0) caminho.moveTo(x, y) else caminho.lineTo(x, y)
    }
    drawPath(
        path = caminho,
        color = tinta,
        style = Stroke(
            width = TRACO.toPx(),
            pathEffect = if (tracejada) PathEffect.dashPathEffect(TRACEJADO) else null,
        ),
    )
}

/**
 * Qual fatia caiu debaixo do dedo, ou nula fora do círculo.
 *
 * `atan2` devolve 0° no eixo horizontal e cresce no sentido do relógio (o eixo
 * Y da tela aponta para baixo); a pizza começa no topo, daí os +90. O `% 360`
 * depois do +360 é o que impede ângulo negativo de virar fatia nenhuma.
 */
private fun fatiaTocada(toque: Offset, tamanho: IntSize, varreduras: List<Float>): Int? {
    val centro = Offset(tamanho.width / 2f, tamanho.height / 2f)
    val vetor = toque - centro
    if (vetor.getDistance() > min(tamanho.width, tamanho.height) / 2f) return null

    val graus =
        (Math.toDegrees(atan2(vetor.y.toDouble(), vetor.x.toDouble())).toFloat() + MEIA_VOLTA) % GRAUS

    // Os limites acumulados de cada fatia: a primeira cujo fim passa do ângulo
    // é a tocada. `runningFold` começa em 0, e o `drop(1)` tira esse zero.
    val limites = varreduras.runningFold(0f) { ate, varredura -> ate + varredura }.drop(1)
    return limites.indexOfFirst { graus <= it }.takeIf { it >= 0 }
}

/** A cor da categoria, ou papel para o grupo sem categoria — que o contorno delimita. */
private fun corDe(state: ReportsState, fatia: GrupoDeCategoria, papel: Color): Color =
    state.categoriaDe(fatia.categoriaId)?.let { Color(it.colorArgb) } ?: papel

/**
 * Arredonda em vez de truncar: com corte, 93,8% vira 93 e a legenda inteira
 * soma 99. O `+ totalCents / 2` é o arredondamento de sempre, feito em inteiro
 * porque dinheiro não passa por `Double` (Art. 6).
 */
private fun percentual(parteCents: Long, totalCents: Long): Int =
    if (totalCents <= 0) 0 else ((parteCents * CEM + totalCents / 2) / totalCents).toInt()

private const val GRAUS = 360f
private const val TOPO = -90f

/** +90 leva o zero do `atan2` para o topo; o +360 tira o negativo antes do resto. */
private const val MEIA_VOLTA = 90f + 360f
private const val CEM = 100L

private val ALTURA_PIZZA = 220.dp
private val ALTURA_LINHA = 140.dp
private val PONTO = 14.dp
private val TRACO = 2.dp
private val TRACEJADO = floatArrayOf(12f, 10f)

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val MES_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM/yy", PT_BR)
