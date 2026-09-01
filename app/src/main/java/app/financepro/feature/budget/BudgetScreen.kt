package app.financepro.feature.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.component.CategorySticker
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Ember
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes
import app.financepro.core.ui.theme.Subheading
import app.financepro.core.ui.theme.Sunburst
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
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cabecalho(mes = state.mes, onAnterior = vm::mesAnterior, onSeguinte = vm::mesSeguinte)

        val progresso = state.progresso
        if (progresso.isEmpty()) {
            // REQ-UI-006 — o vazio traz a ação que o preenche, e aqui são duas:
            // começar do zero, ou repetir o que o mês passado já dizia.
            Text("Nenhum teto neste mês.", style = Body, color = Slush.ink)
        } else {
            progresso.forEach { Linha(it, onClick = { vm.abrirTeto(it.categoria.id) }) }
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

/** Mesma gramática do cabeçalho da lista (T-014): o mês em linha própria. */
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Linha(progresso: BudgetProgress, onClick: () -> Unit) {
    SlushCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(12.dp)
                .semantics(mergeDescendants = true) { contentDescription = falado(progresso) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = progresso.categoria.name,
                    style = BodyStrong,
                    color = Slush.ink,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(cents = progresso.spentCents, style = MoneyCaption)
                // REQ-BUD-003 pede gasto, **limite** e percentual na tela. Sem
                // o limite escrito, "82%" obriga a fazer a conta de cabeça para
                // saber de quanto — e a barra sozinha não dá o número.
                Text("de " + reais(progresso.limitCents), style = Caption, color = Slush.ink)
            }

            Barra(progresso.percent)

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = aviso(progresso),
                    style = Caption,
                    color = Slush.ink,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(text = "Teto", onClick = onClick)
            }
        }
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
    val preenchimento = when {
        percent >= ESTOURO_PERCENT -> Ember
        percent >= ALERTA_PERCENT -> Sunburst
        else -> Slush.ink
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ALTURA_BARRA)
            .clip(Pill)
            .border(OutlineWidth, Slush.ink, Pill),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = (percent / PERCENT_CHEIO).coerceIn(0f, 1f))
                .fillMaxSize()
                .background(if (percent >= ALERTA_PERCENT) preenchimento else Color.Transparent),
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
        shape = SlushShapes.extraLarge,
        containerColor = Slush.paper,
        contentColor = Slush.ink,
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
                color = Slush.ink,
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
private val ALTURA_BARRA = 12.dp

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val MES_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", PT_BR)
