package app.financepro.feature.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.R
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.component.AvatarDeCategoria
import app.financepro.core.ui.component.BotaoCircular
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.component.CategorySticker
import app.financepro.core.ui.component.EstadoVazio
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Danger
import app.financepro.core.ui.theme.DisplaySm
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Subheading
import app.financepro.core.ui.theme.Teal
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Warning
import app.financepro.domain.usecase.ALERTA_PERCENT
import app.financepro.domain.usecase.BudgetProgress
import app.financepro.domain.usecase.ESTOURO_PERCENT
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Orçamento do mês. REQ-BUD-003 · REQ-BUD-005
 *
 * A barra é a única superfície do app com preenchimento saturado, e ela só
 * enche quando há o que avisar: dentro do teto é contorno vazio (design.md), e
 * quem está bem não precisa de cor gritando. Âmbar aos 80%, vermelho aos 100% —
 * sempre **com a palavra junto**, porque cor não é sinal único (REQ-A11Y-003).
 */
@Composable
fun BudgetScreen(vm: BudgetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cabecalho(mes = state.mes, onAnterior = vm::mesAnterior, onSeguinte = vm::mesSeguinte)

        // **Ordenado por urgência, não por cadastro.** Quem estourou primeiro,
        // depois quem está perto do teto. A rolagem vira prioridade: o que exige
        // decisão está no alto, e o que está folgado não disputa a primeira tela.
        val progresso = state.progresso.sortedByDescending { it.percent }
        if (progresso.isNotEmpty()) TetoTotal(progresso)
        if (progresso.isEmpty() && state.carregado) {
            // REQ-UI-006 — o vazio traz a ação que o preenche, e aqui são duas:
            // começar do zero, ou repetir o que o mês passado já dizia.
            EstadoVazio(
                titulo = "SEM TETO NESTE MÊS",
                sticker = Teal,
                icone = R.drawable.ic_orcamento,
                descricao = "Escolha uma categoria e um limite; o resto a tela preenche sozinha.",
            )
        } else {
            CardDeCategorias(progresso, onTeto = { vm.abrirTeto(it) })
        }


        if (state.semTeto.isNotEmpty()) {
            FilledCta(
                text = if (progresso.isEmpty()) "Definir um teto" else "Novo teto",
                onClick = { vm.abrirTeto(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.temMesAnterior) {
            GhostButton(
                text = "Copiar tetos de " + MES_CURTO.format(state.mes.minusMonths(1)),
                onClick = { vm.copiarDoMesAnterior() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.folha != null) {
        FolhaDeTeto(
            state = state,
            onCategoria = vm::escolherCategoria,
            onValor = vm::valor,
            onSalvar = vm::salvarTeto,
            onRemover = vm::removerTeto,
            onDismiss = vm::fecharTeto,
        )
    }
}

/**
 * **Um card para todas**, e não um por categoria. Seis cards empilhados davam a
 * cada teto o peso de uma seção, e a tela virava uma pilha de blocos onde o
 * protótipo tem uma lista.
 */
@Composable
private fun CardDeCategorias(progresso: List<BudgetProgress>, onTeto: (Long) -> Unit) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Por categoria",
                    style = BodyStrong,
                    color = Tema.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (progresso.size == 1) "1 teto" else "${progresso.size} tetos",
                    style = Caption,
                    color = Tema.inkMute,
                )
            }
            progresso.forEach { Linha(it, onClick = { onTeto(it.categoria.id) }) }
        }
    }
}

/**
 * Quanto do teto do mês já foi gasto. REQ-BUD-003 · REQ-DS-009
 *
 * É o **número herói** da tela: antes de olhar categoria por categoria, "estou
 * bem ou não" já tem resposta. A tabela de intensidade dá `DisplaySm` ao
 * orçamento, e até a T-053 nenhuma linha o usava — a tela inteira era feita de
 * corpo de texto do mesmo peso.
 *
 * **Barra, e não anel.** O anel foi a primeira tentativa e estava errado por
 * dois motivos: não é o desenho do protótipo, e um anel que passa de 100% ou dá
 * a volta ou para — nas duas leituras ele mente sobre o estouro. A barra deixa o
 * excesso onde ele se lê, no texto ao lado.
 *
 * Fica sobre o canvas, sem card. O número herói é tipo, não bloco (design.md §1).
 */
@Composable
private fun TetoTotal(progresso: List<BudgetProgress>) {
    val limite = progresso.sumOf { it.limitCents }
    val gasto = progresso.sumOf { it.spentCents }
    val percent = if (limite <= 0) 0 else ((gasto * CEM) / limite).toInt().coerceAtLeast(0)
    val dias = progresso.first().diasRestantes
    val faltam = if (dias == 1) "falta 1 dia" else "faltam $dias dias"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Gasto $percent por cento do teto do mês. " +
                    reais(gasto) + " de " + reais(limite) + ". " + faltam + "."
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("GASTO DO TETO", style = Caption, color = Tema.inkMute)
        Text("$percent%", style = DisplaySm, color = Tema.ink)
        Text(
            text = reais(gasto) + " de " + reais(limite) + " · " + faltam,
            style = Caption,
            color = Tema.inkMute,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(ALTURA_BARRA)
                .clip(Pill)
                .background(Tema.hairline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (percent / CEM.toFloat()).coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(Pill)
                    .background(Tema.ink),
            )
        }
    }
}

private const val CEM = 100L

/**
 * O adesivo da categoria na linha de teto.
 *
 * 28dp: menor que o da lista de transações, porque aqui acompanha uma linha de
 * texto e não duas. Continua acima dos 24dp de REQ-DS-006, então dispensa anel.
 */
private val AVATAR_TETO = 28.dp

/** Mesma gramática do cabeçalho da lista (T-014): o mês em linha própria. */
@Composable
private fun Cabecalho(mes: YearMonth, onAnterior: () -> Unit, onSeguinte: () -> Unit) {
    // Título e setas na mesma linha, como na lista de transações. O `weight` no
    // título é quem cresce com a fonte a 200% (REQ-A11Y-004).
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MES.format(mes).replaceFirstChar { it.uppercase() },
            style = Subheading,
            color = Tema.ink,
            modifier = Modifier.weight(1f),
        )
        BotaoCircular(R.drawable.ic_voltar, "Mês anterior", onAnterior)
        BotaoCircular(R.drawable.ic_avancar, "Próximo mês", onSeguinte)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Linha(progresso: BudgetProgress, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Editar teto", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = falado(progresso) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // A cor da categoria é a identidade dela, e faltava aqui: a linha
            // dizia o nome e o estado, mas não *qual* categoria era.
            AvatarDeCategoria(
                colorArgb = progresso.categoria.colorArgb,
                iconKey = progresso.categoria.iconKey,
                tamanho = AVATAR_TETO,
            )
            Text(
                text = progresso.categoria.name,
                style = BodyStrong,
                color = Tema.ink,
                modifier = Modifier.weight(1f),
            )
            MoneyText(cents = progresso.spentCents, style = MoneyCaption)
            // REQ-BUD-003 pede gasto, **limite** e percentual na tela. Sem o
            // limite escrito, "82%" obriga a fazer a conta de cabeça para saber
            // de quanto — e a barra sozinha não dá o número.
            Text("de " + reais(progresso.limitCents), style = Caption, color = Tema.inkMute)
        }

        Barra(progresso.percent)

        // O aviso carrega o que a barra não diz: o percentual (REQ-BUD-003) e a
        // sobra diária ou o excesso (REQ-BUD-004). O protótipo não os mostra; a
        // spec exige, e é ele que fica.
        Text(text = aviso(progresso), style = Caption, color = Tema.inkMute)
    }
}



/**
 * A barra de REQ-BUD-003.
 *
 * Contorno sempre, preenchimento só a partir dos 80%: uma barra que enche desde
 * o primeiro real gasta transforma "gastei um pouco" em alerta visual, e o
 * alerta perde o sentido quando está sempre ligado.
 */
@Composable
private fun Barra(percent: Int) {
    // Cresce de zero até o valor, e refaz o caminho quando o mês muda. É a
    // única tela onde a barra **é** o dado: ver o traço parar nos 78% diz mais
    // que encontrá-lo parado ali. `Animatable` e não `animateFloatAsState`
    // porque este começa no valor final — não haveria caminho nenhum.
    val alvo = (percent / PERCENT_CHEIO).coerceIn(0f, 1f)
    val fracao = remember { Animatable(0f) }
    LaunchedEffect(alvo) { fracao.animateTo(alvo, tween(CRESCIMENTO_MS, easing = FastOutSlowInEasing)) }

    val preenchimento = when {
        percent >= ESTOURO_PERCENT -> Danger
        percent >= ALERTA_PERCENT -> Warning
        else -> Tema.ink
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ALTURA_BARRA)
            .clip(Pill)
            .background(Tema.hairline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = fracao.value)
                .fillMaxSize()
                .clip(Pill)
                .background(preenchimento),
        )
    }
}

/**
 * O que a barra sozinha não diz. REQ-BUD-003 · REQ-BUD-004 · REQ-A11Y-003
 *
 * O ícone vem **com a palavra**: um triângulo sozinho é cor com outra forma, e
 * quem usa leitor de tela ouviria "triângulo apontando para cima".
 */
private fun aviso(p: BudgetProgress): String = when {
    p.estourouCents > 0 -> "▲ Estourou " + reais(p.estourouCents) + " · " + p.percent + "%"
    p.percent >= ALERTA_PERCENT -> "⚠ Atenção · " + p.percent + "% · " + porDia(p)
    else -> p.percent.toString() + "% · " + porDia(p)
}

private fun porDia(p: BudgetProgress) = reais(p.sobraDiariaCents) + " por dia"

/** O que o leitor de tela lê na linha inteira, numa frase só (REQ-A11Y-001). */
private fun falado(p: BudgetProgress): String =
    p.categoria.name + ": " + reais(p.spentCents) + " de " + reais(p.limitCents) + ", " + aviso(p)
        .replace("▲ ", "")
        .replace("⚠ ", "")

/**
 * O valor dentro de uma frase.
 *
 * `MoneyText` continua sendo quem desenha valor sozinho na tela; aqui o número
 * entra no meio de um texto, e um composable não cabe dentro de uma `String`. A
 * formatação é a mesma `formatBRL` de sempre, e não uma segunda (Art. 6).
 */
private fun reais(cents: Long): String = formatBRL(cents)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDeTeto(
    state: BudgetState,
    onCategoria: (Long) -> Unit,
    onValor: (Long) -> Unit,
    onSalvar: () -> Unit,
    onRemover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val folha = state.folha ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = Formas.extraLarge,
        containerColor = Tema.paper,
        contentColor = Tema.ink,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.categoriaDaFolha?.name ?: "Teto de qual categoria?",
                style = Subheading,
                color = Tema.ink,
            )

            if (folha.categoriaId == null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.semTeto, key = { it.id }) { categoria ->
                        CategorySticker(
                            category = categoria,
                            selecionado = false,
                            onClick = { onCategoria(categoria.id) },
                        )
                    }
                }
            } else {
                Rotulo("Teto do mês")
                MoneyField(cents = folha.cents, onCentsChange = onValor, autoFocus = true)
                FilledCta(text = "Salvar", onClick = onSalvar, modifier = Modifier.fillMaxWidth())
                GhostButton(text = "Remover teto", onClick = onRemover, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private const val PERCENT_CHEIO = 100f

/** O tempo do traço. Curto: a barra é dado, não abertura de tela. */
private const val CRESCIMENTO_MS = 420
private val ALTURA_BARRA = 12.dp

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val MES_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", PT_BR)
