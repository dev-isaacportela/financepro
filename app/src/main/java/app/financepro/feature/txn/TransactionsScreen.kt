package app.financepro.feature.txn

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import app.financepro.R
import app.financepro.core.ui.component.BotaoCircular
import app.financepro.core.ui.component.Chip
import app.financepro.core.ui.component.EstadoVazio
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.LinhaDeTransacao
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Superficie
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Subheading
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Warning
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.DiaDeTransacoes
import app.financepro.domain.usecase.EscopoDeParcela
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

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
fun TransactionsScreen(
    onNovoLancamento: () -> Unit,
    onEditar: (Long) -> Unit,
    vm: TransactionsViewModel = hiltViewModel(),
) {
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
        val quantidade = state.ultimaQuantidade
        val resposta = withTimeoutOrNull(DESFAZER_MS) {
            snackbar.showSnackbar(
                // Doze linhas apagadas anunciadas no singular seriam a barra
                // dizendo menos do que aconteceu, logo antes de o desfazer sumir.
                message = if (quantidade > 1) "$quantidade parcelas excluídas" else "Transação excluída",
                actionLabel = "Desfazer",
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (resposta == SnackbarResult.ActionPerformed) vm.desfazer()
    }

    Scaffold(
        containerColor = Tema.paper,
        snackbarHost = { SnackbarHost(snackbar) { BarraDesfazer(it) } },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) {
            Cabecalho(
                titulo = tituloDoPeriodo(state),
                filtrosAtivos = state.filtro.ativo,
                tipo = state.filtro.tipo,
                onTipo = vm::tipo,
                onAnterior = vm::mesAnterior,
                onSeguinte = vm::mesSeguinte,
                onFiltrar = { filtrando = true },
            )

            val dias = state.dias
            when {
                dias.isNotEmpty() ->
                    Lista(state = state, dias = dias, onExcluir = vm::excluir, onEditar = onEditar)
                // Só depois da primeira emissão: antes dela a lista está vazia
                // porque ainda não chegou, e o vazio afirmaria o que não sabe.
                state.carregado -> Vazio(
                    comFiltro = state.filtro.ativo,
                    onLancar = onNovoLancamento,
                    onLimpar = vm::limparFiltros,
                )
            }
        }
    }

    // REQ-TXN-009 — a pergunta antes de apagar uma parcela.
    state.excluindo?.let { parcela ->
        EscopoDeExclusaoSheet(
            parcela = parcela,
            onEscopo = vm::excluirComEscopo,
            onDismiss = vm::cancelarExclusao,
        )
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
    tipo: TxnType?,
    onTipo: (TxnType?) -> Unit,
    onAnterior: () -> Unit,
    onSeguinte: () -> Unit,
    onFiltrar: () -> Unit,
) {
    // Título em linha própria, e não espremido entre os botões: "Agosto de 2026"
    // não cabe ao lado de três pílulas em 360dp e saía cortado em "Agosto de" —
    // o aparelho mostrou, o emulador não. Em linha própria ele tem a largura
    // toda, o que também é o que sobrevive à fonte a 200% (REQ-A11Y-004).
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(titulo, style = Subheading, color = Tema.ink)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            BotaoCircular(R.drawable.ic_voltar, "Mês anterior", onAnterior)
            BotaoCircular(R.drawable.ic_avancar, "Próximo mês", onSeguinte)
        }

        // Rolagem horizontal: a 200% de fonte os quatro chips passam da largura,
        // e cortar "Filtros" tiraria o acesso ao resto dos recortes
        // (REQ-A11Y-004).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Tudo", tipo == null, onClick = { onTipo(null) })
            Chip("Entradas", tipo == TxnType.INCOME, onClick = { onTipo(TxnType.INCOME) })
            Chip("Saídas", tipo == TxnType.EXPENSE, onClick = { onTipo(TxnType.EXPENSE) })
            // O ponto no rótulo sinaliza filtro ativo além da cor (REQ-A11Y-003).
            // `Filtros` não é um recorte, é a folha com os demais — por isso ele
            // nunca aparece selecionado, mesmo com filtro ligado.
            Chip(if (filtrosAtivos) "Filtros •" else "Filtros", selecionado = false, onClick = onFiltrar)
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
            EstadoVazio(
                titulo = "NADA CASA",
                sticker = Warning,
                descricao = "Nenhum lançamento passa por esses filtros.",
            )
            GhostButton(text = "Limpar filtros", onClick = onLimpar)
        } else {
            EstadoVazio(
                titulo = "MÊS EM BRANCO",
                sticker = Warning,
                descricao = "Nenhum lançamento neste período.",
            )
            FilledCta(text = "Lançar", onClick = onLancar)
        }
    }
}

@Composable
private fun Lista(
    state: TransactionsState,
    dias: List<DiaDeTransacoes>,
    onExcluir: (Txn) -> Unit,
    onEditar: (Long) -> Unit,
) {
    val saldos = state.saldos
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        dias.forEach { dia ->
            item(key = "dia-" + dia.data) { CabecalhoDoDia(dia) }
            items(dia.itens, key = { it.id }) { txn ->
                // Excluir, desfazer e trocar de filtro mexem na lista o tempo
                // todo; uma linha por chave, e o resto se acomoda sozinho.
                Deslizavel(onExcluir = { onExcluir(txn) }, modifier = Modifier.animateItem()) {
                    LinhaDeTransacao(
                        txn = txn,
                        categoria = state.categoriaDe(txn.categoryId),
                        conta = state.contaDe(txn.accountId),
                        destino = state.contaDe(txn.counterAccountId),
                        saldoCents = saldos[txn.id],
                        // Editar é a ação primária da linha; excluir continua no
                        // deslize e na ação personalizada do leitor de tela.
                        onClick = { onEditar(txn.id) },
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
        Text(DIA.format(dia.data), style = Caption, color = Tema.ink, modifier = Modifier.weight(1f))
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
private fun Deslizavel(
    onExcluir: () -> Unit,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit,
) {
    val estado = rememberSwipeToDismissBoxState(
        // **Nunca** confirma a dispensa: o gesto pede a exclusão, e quem tira a
        // linha da tela é o banco emitindo a lista sem ela.
        //
        // Confirmando, a caixa guarda "dispensada" no `remember` da linha — e um
        // desfazer rápido, antes de o Room emitir, devolvia a transação para uma
        // linha que continuava desenhada como o fundo vermelho de "Excluir",
        // travada assim até outra recomposição. Encontrado no aparelho tocando
        // "Desfazer" um segundo depois do deslize. Serve também à parcela, que
        // primeiro pergunta o escopo (REQ-TXN-009) e pode nem excluir.
        confirmValueChange = { valor ->
            if (valor == SwipeToDismissBoxValue.EndToStart) onExcluir()
            false
        },
    )
    SwipeToDismissBox(
        state = estado,
        enableDismissFromStartToEnd = false,
        backgroundContent = { FundoExcluir() },
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Excluir") { onExcluir(); true })
        },
        content = { conteudo() },
    )
}

/**
 * REQ-TXN-009 — excluir parcela pergunta o escopo.
 *
 * Folha com as três opções escritas por extenso, e não um diálogo de "tem
 * certeza?": a pergunta aqui não é se apaga, é **o que** apaga, e uma escolha de
 * três não cabe em sim/não. Cada opção é um alvo de largura inteira, o que
 * também resolve o toque de 48dp (REQ-A11Y-002).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EscopoDeExclusaoSheet(
    parcela: Txn,
    onEscopo: (EscopoDeParcela) -> Unit,
    onDismiss: () -> Unit,
) {
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Excluir o quê?", style = Subheading, color = Tema.ink)
            val indice = parcela.installmentIndex
            val total = parcela.installmentTotal
            if (indice != null && total != null) {
                Text("Esta é a parcela $indice de $total.", style = Body, color = Tema.ink)
            }
            EscopoDeParcela.entries.forEach { escopo ->
                GhostButton(
                    text = rotuloDoEscopo(escopo),
                    onClick = { onEscopo(escopo) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun rotuloDoEscopo(escopo: EscopoDeParcela) = when (escopo) {
    EscopoDeParcela.SO_ESTA -> "Só esta parcela"
    EscopoDeParcela.ESTA_E_FUTURAS -> "Esta e as futuras"
    EscopoDeParcela.TODAS -> "Todas as parcelas"
}

/** A palavra, não só o fundo: cor nunca é sinal único (REQ-A11Y-003). */
@Composable
private fun FundoExcluir() = Box(
    modifier = Modifier
        .fillMaxSize()
        // **O mesmo raio do card da linha.** Com 12dp atrás de um card de 20dp,
        // o fundo aparecia pelos quatro cantos mesmo em repouso — e como `ink` é
        // branco no tema escuro, cada linha da lista ganhava um contorno que
        // ninguém desenhou. Parecia moldura; era o fundo de "Excluir" vazando.
        .clip(Formas.medium)
        .background(Tema.ink)
        .padding(horizontal = 16.dp),
    contentAlignment = Alignment.CenterEnd,
) {
    Text("Excluir", style = BodyStrong, color = Tema.paper)
}

@Composable
private fun BarraDesfazer(dados: SnackbarData) {
    // `Superficie` e não o `Snackbar` do Material: aquele traz sombra por
    // padrão, e uma elevação que ninguém escreve não é pega pelo TokenLintTest
    // — violaria REQ-DS-004 em silêncio.
    Superficie(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = Pill) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dados.visuals.message, style = Body, color = Tema.ink)
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
