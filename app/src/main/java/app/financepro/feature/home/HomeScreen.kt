package app.financepro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.LinhaDeTransacao
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.component.SlushFab
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Label
import app.financepro.core.ui.theme.MoneyBody
import app.financepro.core.ui.theme.MoneyLg
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.usecase.Comparativo

/**
 * O dashboard. REQ-UI-004 · REQ-UI-006 · Art. 18
 *
 * A ordem dos blocos é a do requisito. Dois dos seis dele não aparecem aqui:
 * orçamento depende de REQ-BUD-001 e próximas contas de REQ-REC-008, ambos F1
 * — antes disso não há nem dado nem ação que os preencha, e um bloco
 * permanentemente mudo é o oposto do que REQ-UI-006 pede. A spec registra a
 * regra; eles entram com as tasks deles.
 *
 * `Column` com `verticalScroll` e não `LazyColumn`: o conteúdo é limitado por
 * construção — dois cards e cinco linhas. `LazyColumn` custaria chaves,
 * reciclagem e um `contentPadding` para adiar trabalho que não existe.
 *
 * O FAB fica **fora** da rolagem, no `Box`: despesa a partir da tela inicial em
 * três toques é o Art. 18, e um botão que sai de vista ao rolar acrescenta um
 * toque de volta antes do primeiro.
 */
@Composable
fun HomeScreen(
    onNovoLancamento: () -> Unit,
    onVerContas: () -> Unit,
    onVerTransacoes: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Saldo(state.saldoCents)
            Cartoes(state, onVerContas)

            if (state.vazio) {
                Comeco(onNovoLancamento)
            } else {
                ComparativoDoPeriodo(state.comparativo)
                Ultimas(state, onVerTransacoes)
            }
        }

        SlushFab(
            onClick = onNovoLancamento,
            rotulo = "Novo lançamento",
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            // Sem biblioteca de ícones: um "+" em Label diz a mesma coisa e não
            // acrescenta dependência para desenhar um sinal de mais.
            Text("+", style = Label)
        }
    }
}

@Composable
private fun Saldo(cents: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SALDO TOTAL", style = Caption, color = Slush.ink)
        MoneyText(cents = cents, style = MoneyLg)
    }
}

/**
 * Cartão sem fatura ainda: fatura, vencimento e limite disponível são
 * REQ-CARD-005 e REQ-CARD-008, da T-024/T-025. O que a F0 sabe dizer é a
 * dívida, que `cardDebt` calcula e testa desde a T-008 — e que até agora não
 * tinha um único chamador.
 */
@Composable
private fun Cartoes(state: HomeState, onVerContas: () -> Unit) {
    Bloco(titulo = "Cartões") {
        if (state.cartoes.isEmpty()) {
            // REQ-UI-006 — o vazio traz a ação que o preenche.
            Text("Nenhum cartão cadastrado.", style = Body, color = Slush.ink)
            GhostButton(text = "Adicionar cartão", onClick = onVerContas)
        } else {
            Valor(rotulo = "A pagar", cents = state.dividaCents)
            Text(
                text = state.cartoes.joinToString(" · ") { it.name },
                style = Caption,
                color = Slush.ink.copy(alpha = SECUNDARIO_ALPHA),
            )
        }
    }
}

/**
 * REQ-UI-004 — entrou, saiu, e como isso se compara ao mês anterior.
 *
 * A variação chega **por palavra** além do sinal: cor nunca é sinal único
 * (REQ-A11Y-003), e o valor fica em tinta neutra como REQ-DS-007 exige — nada
 * de verde para receita e vermelho para despesa.
 */
@Composable
private fun ComparativoDoPeriodo(c: Comparativo) {
    Bloco(titulo = "Este mês") {
        Valor(rotulo = "Receitas", cents = c.receitasCents)
        Valor(rotulo = "Despesas", cents = c.despesasCents)
        Valor(rotulo = "Sobrou", cents = c.liquidoCents)
        Valor(rotulo = "Ante o mês anterior", cents = c.deltaCents)
        Text(
            text = variacaoEmPalavras(c.deltaCents),
            style = Caption,
            color = Slush.ink.copy(alpha = SECUNDARIO_ALPHA),
        )
    }
}

@Composable
private fun Ultimas(state: HomeState, onVerTransacoes: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Últimas transações", style = Subheading, color = Slush.ink)
        state.ultimas.forEach { txn ->
            LinhaDeTransacao(
                txn = txn,
                categoria = state.categoriaDe(txn.categoryId),
                conta = state.contaDe(txn.accountId),
                destino = state.contaDe(txn.counterAccountId),
            )
        }
        GhostButton(text = "Ver todas", onClick = onVerTransacoes)
    }
}

/** REQ-UI-006 — a tela inteira sem dados traz a ação que a preenche. */
@Composable
private fun Comeco(onNovoLancamento: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nenhum lançamento ainda.", style = Body, color = Slush.ink)
        FilledCta(text = "Lançar", onClick = onNovoLancamento)
    }
}

@Composable
private fun Bloco(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = Subheading, color = Slush.ink)
        SlushCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { conteudo() }
        }
    }
}

/**
 * Rótulo e valor lado a lado, com o rótulo em `weight` — assim ele **quebra**
 * em vez de truncar quando a fonte vai a 200% (REQ-A11Y-004). É o mesmo motivo
 * que tirou o título do meio das pílulas em `TransactionsScreen`.
 */
@Composable
private fun Valor(rotulo: String, cents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(rotulo, style = Body, color = Slush.ink, modifier = Modifier.weight(1f))
        MoneyText(cents = cents, style = MoneyBody)
    }
}

private fun variacaoEmPalavras(deltaCents: Long): String = when {
    deltaCents > 0 -> "Sobrou mais que no mês anterior"
    deltaCents < 0 -> "Sobrou menos que no mês anterior"
    else -> "Igual ao mês anterior"
}

/** Mesmo recuo do subtítulo da linha de transação (design.md §6.3). */
private const val SECUNDARIO_ALPHA = 0.62f
