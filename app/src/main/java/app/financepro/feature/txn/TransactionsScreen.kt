package app.financepro.feature.txn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.LinhaDeTransacao
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.SlushSurface
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.DiaDeTransacoes
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lista de transações. REQ-TXN-010 · REQ-TXN-011 · REQ-TXN-012 · REQ-UI-006
 *
 * A tela mais densa do app, e por isso a que menos tolera vocabulário de
 * pôster. A linha em si mora em `core/ui/component/TxnRow.kt` desde que o
 * dashboard (T-017) virou o segundo chamador; o que sobrou aqui é o que é da
 * tela — período, filtro, agrupamento por dia e o desfazer de 5s.
 *
 * ponytail: sem Paging 3 (ADR-009). A visão padrão é um mês, ~100 linhas, e
 * carregá-las em memória custa menos que `PagingSource`, estados de load e os
 * testes deles. Teto: o filtro "Tudo" e o extrato de conta trazem o histórico
 * inteiro — trocar por Paging 3 acima de ~5.000 linhas.
 */
@Composable
fun TransactionsScreen(onNovoLancamento: () -> Unit, vm: TransactionsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var filtrando by remember { mutableStateOf(false) }

    // REQ-TXN-010 — cinco segundos, nem quatro nem dez. `SnackbarDuration.Short`
    // são ~4s e `Long` ~10s; nenhum é o que o requisito pede. `Indefinite`
    // dentro de um `withTimeoutOrNull` dá a duração exata, e o cancelamento do
    // timeout é o que fecha a barra.
    //
    // A chave é o **contador** de exclusões, não uma bandeira: com um booleano
    // já ligado, excluir uma segunda linha antes dos 5s não reiniciaria este
    // efeito, e a segunda exclusão ficaria sem desfazer nenhum.
    LaunchedEffect(state.exclusoes) {
        if (state.exclusoes == 0) return@LaunchedEffect
        val resposta = withTimeoutOrNull(DESFAZER_MS) {
            snackbar.showSnackbar(
                message = "Transação excluída",
                actionLabel = "Desfazer",
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (resposta == SnackbarResult.ActionPerformed) vm.desfazer()
    }

    Scaffold(
        containerColor = Slush.paper,
        snackbarHost = { SnackbarHost(snackbar) { BarraDesfazer(it) } },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
            Cabecalho(
                titulo = tituloDoPeriodo(state),
                filtrosAtivos = state.filtro.ativo,
                onAnterior = vm::mesAnterior,
                onSeguinte = vm::mesSeguinte,
                onFiltrar = { filtrando = true },
            )

            val dias = state.dias
            if (dias.isEmpty()) {
                Vazio(
                    comFiltro = state.filtro.ativo,
                    onLancar = onNovoLancamento,
                    onLimpar = vm::limparFiltros,
                )
            } else {
                Lista(state = state, dias = dias, onExcluir = vm::excluir)
            }
        }
    }

    if (filtrando) {
        TxnFilterSheet(
            state = state,
            onFiltro = vm::aplicar,
            onTodoOPeriodo = vm::todoOPeriodo,
            onLimpar = vm::limparFiltros,
            onDismiss = { filtrando = false },
        )
    }
}

@Composable
private fun Cabecalho(
    titulo: String,
    filtrosAtivos: Boolean,
    onAnterior: () -> Unit,
    onSeguinte: () -> Unit,
    onFiltrar: () -> Unit,
) {
    // Título em linha própria, e não espremido entre os botões: "Agosto de 2026"
    // não cabe ao lado de três pílulas em 360dp e saía cortado em "Agosto de" —
    // o aparelho mostrou, o emulador não. Em linha própria ele tem a largura
    // toda, o que também é o que sobrevive à fonte a 200% (REQ-A11Y-004).
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(titulo, style = Subheading, color = Slush.ink)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(text = "◀", onClick = onAnterior)
            GhostButton(text = "▶", onClick = onSeguinte)
            Spacer(Modifier.weight(1f))
            // O ponto no rótulo sinaliza filtro ativo além da cor (REQ-A11Y-003).
            GhostButton(text = if (filtrosAtivos) "Filtros •" else "Filtros", onClick = onFiltrar)
        }
    }
}

/** REQ-UI-006 — toda tela sem dados traz a ação que a preenche. */
@Composable
private fun Vazio(comFiltro: Boolean, onLancar: () -> Unit, onLimpar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // São dois vazios diferentes, e a ação certa para cada um é outra:
        // filtro que não casa se resolve limpando, mês sem lançamento se
        // resolve lançando. Um texto só para os dois mandaria metade das
        // pessoas para o botão errado.
        if (comFiltro) {
            Text("Nada casa com esses filtros.", style = Body, color = Slush.ink)
            GhostButton(text = "Limpar filtros", onClick = onLimpar)
        } else {
            Text("Nenhum lançamento neste período.", style = Body, color = Slush.ink)
            FilledCta(text = "Lançar", onClick = onLancar)
        }
    }
}

@Composable
private fun Lista(state: TransactionsState, dias: List<DiaDeTransacoes>, onExcluir: (Txn) -> Unit) {
    val saldos = state.saldos
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        dias.forEach { dia ->
            item(key = "dia-" + dia.data) { CabecalhoDoDia(dia) }
            items(dia.itens, key = { it.id }) { txn ->
                Deslizavel(onExcluir = { onExcluir(txn) }) {
                    LinhaDeTransacao(
                        txn = txn,
                        categoria = state.categoriaDe(txn.categoryId),
                        conta = state.contaDe(txn.accountId),
                        destino = state.contaDe(txn.counterAccountId),
                        saldoCents = saldos[txn.id],
                    )
                }
            }
        }
    }
}

/** REQ-TXN-011 — a data e o líquido do dia. */
@Composable
private fun CabecalhoDoDia(dia: DiaDeTransacoes) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `weight` na data, não no valor: num Row os filhos SEM peso são
        // medidos primeiro e ficam com a largura que quiserem. Com a fonte a
        // 200% a data engolia a linha e o total sobrava com uma coluna de um
        // caractere — "−R / $ / 18, / 50", quatro linhas (REQ-A11Y-004).
        Text(DIA.format(dia.data), style = Caption, color = Slush.ink, modifier = Modifier.weight(1f))
        MoneyText(cents = dia.totalCents, style = MoneyCaption)
    }
}

/**
 * REQ-TXN-010 — swipe exclui na hora, sem diálogo.
 *
 * A ação de acessibilidade não é enfeite: com o TalkBack ligado o gesto de
 * arrastar não chega ao componente, então **excluir seria inalcançável** para
 * quem usa leitor de tela (Art. 17, REQ-A11Y-001). São quatro linhas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Deslizavel(onExcluir: () -> Unit, conteudo: @Composable () -> Unit) {
    val estado = rememberSwipeToDismissBoxState(
        confirmValueChange = { valor ->
            (valor == SwipeToDismissBoxValue.EndToStart).also { if (it) onExcluir() }
        },
    )
    SwipeToDismissBox(
        state = estado,
        enableDismissFromStartToEnd = false,
        backgroundContent = { FundoExcluir() },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Excluir") { onExcluir(); true })
        },
        content = { conteudo() },
    )
}

/** A palavra, não só o fundo: cor nunca é sinal único (REQ-A11Y-003). */
@Composable
private fun FundoExcluir() = Box(
    modifier = Modifier
        .fillMaxSize()
        .clip(SlushShapes.small)
        .background(Slush.ink)
        .padding(horizontal = 16.dp),
    contentAlignment = Alignment.CenterEnd,
) {
    Text("Excluir", style = BodyStrong, color = Slush.paper)
}

@Composable
private fun BarraDesfazer(dados: SnackbarData) {
    // `SlushSurface` e não o `Snackbar` do Material: aquele traz sombra por
    // padrão, e uma elevação que ninguém escreve não é pega pelo TokenLintTest
    // — violaria REQ-DS-004 em silêncio.
    SlushSurface(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = Pill) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dados.visuals.message, style = Body, color = Slush.ink)
            dados.visuals.actionLabel?.let { rotulo ->
                GhostButton(text = rotulo, onClick = { dados.performAction() })
            }
        }
    }
}

private fun tituloDoPeriodo(state: TransactionsState): String =
    if (state.periodoTodo) "Tudo" else MES.format(state.mes).replaceFirstChar { it.uppercase() }

private const val DESFAZER_MS = 5_000L

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)
