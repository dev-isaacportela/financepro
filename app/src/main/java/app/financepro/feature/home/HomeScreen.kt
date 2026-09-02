package app.financepro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.LinhaDeTransacao
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.component.Fab
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Label
import app.financepro.core.ui.theme.MoneyBody
import app.financepro.core.ui.theme.MoneyLg
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.Comparativo

/**
 * O dashboard. REQ-UI-004 · REQ-UI-006 · Art. 18
 *
 * A ordem dos blocos é a do requisito. Falta um dos seis: orçamento, que
 * depende do dashboard saber qual teto está mais perto de estourar — a tela
 * própria dele já existe (T-029), o bloco daqui não. Próximas contas entrou com
 * a T-032, e aparece **só quando há alguma**: com a fonte de dados existindo, um
 * bloco vazio todo dia para quem não tem conta a vencer é o ruído que
 * REQ-UI-006 recusa, e a ação que o preenche mora em Mais › Recorrências.
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
    onEditar: (Long) -> Unit,
    onVerCartao: (Long) -> Unit,
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
            Saldo(state)
            Cartoes(state, onVerContas, onVerCartao)

            if (state.vazio) {
                Comeco(onNovoLancamento)
            } else {
                ComparativoDoPeriodo(state.comparativo)
                if (state.proximas.isNotEmpty()) {
                    ProximasContas(state, vm::efetivar, onEditar)
                }
                Ultimas(state, onVerTransacoes, onEditar)
            }
        }

    }
}

/**
 * O saldo. **Tipo sobre o canvas, sem bloco.**
 *
 * A ênfase vem do tamanho e do vazio ao redor, não de um retângulo colorido: em
 * 34sp com entrelinha travada, o número já é a única coisa grande da tela. Um
 * preenchimento saturado atrás dele seria uma segunda ênfase para a mesma
 * informação, e gastaria o cobalto, que o sistema pede escasso.
 *
 * A versão anterior era um bloco cheio, herdado do sistema visual antigo e
 * recolorido junto com a paleta. **Recolorir não é traduzir**: o bloco existia lá
 * porque a profundidade vinha de banda de cor, e aqui ela vem do degrau de
 * luminância — o retângulo não tinha mais trabalho para fazer.
 *
 * Sem padding horizontal próprio: o alinhamento é o da coluna, o mesmo dos
 * títulos "Cartões" e "Este mês" logo abaixo.
 *
 * Abaixo do número vêm **duas coisas que o saldo não responde**: quanto do que
 * está ali já tem dono (a fatura do cartão) e quanto sobrou no mês. São os dois
 * atalhos que evitam rolar a tela para decidir se dá para gastar hoje.
 */
@Composable
private fun Saldo(state: HomeState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("SALDO TOTAL", style = Caption, color = Tema.inkMute)
        MoneyText(cents = state.saldoCents, style = MoneyLg)
        Text(text = contexto(state), style = Caption, color = Tema.inkMute)

        if (!state.vazio) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Metrica("A pagar no cartão", state.dividaCents, Modifier.weight(1f))
                Metrica("Sobrou no mês", state.comparativo.liquidoCents, Modifier.weight(1f))
            }
        }
    }
}

/** "3 contas · 1 cartão" — o que o número grande não diz sozinho. */
private fun contexto(state: HomeState): String {
    val comuns = state.contas.count { !it.isCard && !it.archived }
    val cartoes = state.cartoes.size
    val a = if (comuns == 1) "1 conta" else "$comuns contas"
    return if (cartoes == 0) a else a + " · " + if (cartoes == 1) "1 cartão" else "$cartoes cartões"
}

/**
 * Um número com rótulo, em bloco próprio.
 *
 * Substitui a linha "rótulo à esquerda, valor à direita" onde o valor é para ser
 * **lido de relance**, e não conferido contra o extrato: quatro dessas linhas
 * empilhadas têm todas o mesmo peso, e nenhuma vence. Em bloco, o olho separa.
 */
@Composable
private fun Metrica(rotulo: String, cents: Long, modifier: Modifier = Modifier) {
    Cartao(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(rotulo, style = Caption, color = Tema.inkMute)
            MoneyText(cents = cents)
        }
    }
}

/**
 * Cartão sem fatura ainda: fatura, vencimento e limite disponível são
 * REQ-CARD-005 e REQ-CARD-008, da T-024/T-025. O que a F0 sabe dizer é a
 * dívida, que `cardDebt` calcula e testa desde a T-008 — e que até agora não
 * tinha um único chamador.
 */
@Composable
private fun Cartoes(state: HomeState, onVerContas: () -> Unit, onVerCartao: (Long) -> Unit) {
    Bloco(titulo = "Cartões") {
        if (state.cartoes.isEmpty()) {
            // REQ-UI-006 — o vazio traz a ação que o preenche.
            Text("Nenhum cartão cadastrado.", style = Body, color = Tema.ink)
            GhostButton(text = "Adicionar cartão", onClick = onVerContas)
        } else {
            // Sem "A pagar" aqui: ele subiu para o par de atalhos do saldo, e o
            // que sobra para este bloco é a navegação por cartão.
            // Um botão por cartão, e não os nomes numa linha só: é daqui que se
            // chega à fatura (T-025), e texto corrido não diz que é tocável.
            state.cartoes.forEach { cartao ->
                GhostButton(
                    text = cartao.name,
                    onClick = { onVerCartao(cartao.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Este mês", style = Subheading, color = Tema.ink)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Metrica("Receitas", c.receitasCents, Modifier.weight(1f))
            Metrica("Despesas", c.despesasCents, Modifier.weight(1f))
        }
        // "Sobrou" subiu para o par de atalhos do saldo; aqui fica a comparação,
        // que é a única das quatro que não é um saldo do mês e sim um delta.
        Cartao(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Valor(rotulo = "Ante o mês anterior", cents = c.deltaCents)
                Text(text = variacaoEmPalavras(c.deltaCents), style = Caption, color = Tema.inkMute)
            }
        }
    }
}

/**
 * REQ-REC-008 · REQ-TXN-006 — o que vence na semana e ainda não foi pago.
 *
 * A mesma linha da lista de transações, com um botão embaixo. A alternativa
 * seria uma linha própria com a ação dentro dela, e aí seriam duas linhas de
 * transação no app para divergirem no primeiro ajuste.
 *
 * O botão leva `contentDescription` porque cinco botões "Efetivar" seguidos são
 * cinco vezes a mesma palavra para quem ouve a tela, sem dizer qual conta
 * (REQ-A11Y-001). O texto visível continua curto — a linha logo acima já diz o
 * resto para quem enxerga.
 */
@Composable
private fun ProximasContas(state: HomeState, onEfetivar: (Txn) -> Unit, onEditar: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Próximas contas", style = Subheading, color = Tema.ink)
        state.proximas.forEach { txn ->
            val categoria = state.categoriaDe(txn.categoryId)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinhaDeTransacao(
                    txn = txn,
                    categoria = categoria,
                    conta = state.contaDe(txn.accountId),
                    destino = state.contaDe(txn.counterAccountId),
                    onClick = { onEditar(txn.id) },
                )
                GhostButton(
                    text = "Efetivar",
                    onClick = { onEfetivar(txn) },
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Efetivar " + txn.description.ifBlank { categoria?.name ?: "lançamento" }
                    },
                )
            }
        }
    }
}

@Composable
private fun Ultimas(state: HomeState, onVerTransacoes: () -> Unit, onEditar: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Últimas transações", style = Subheading, color = Tema.ink)
        // Um card com as linhas dentro, e não um card por linha: cinco
        // retângulos empilhados davam a cada transação o peso de uma seção.
        Cartao(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                state.ultimas.forEach { txn ->
                    LinhaDeTransacao(
                        txn = txn,
                        categoria = state.categoriaDe(txn.categoryId),
                        conta = state.contaDe(txn.accountId),
                        destino = state.contaDe(txn.counterAccountId),
                        // A mesma linha da lista, o mesmo toque: comportamento
                        // que muda de tela para tela faz o usuário parar de
                        // tentar.
                        onClick = { onEditar(txn.id) },
                    )
                }
            }
        }
        GhostButton(text = "Ver todas", onClick = onVerTransacoes)
    }
}

/** REQ-UI-006 — a tela inteira sem dados traz a ação que a preenche. */
@Composable
private fun Comeco(onNovoLancamento: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nenhum lançamento ainda.", style = Body, color = Tema.ink)
        FilledCta(text = "Lançar", onClick = onNovoLancamento)
    }
}

/**
 * Bloco de seção do dashboard: título fora, card contornado dentro.
 *
 * Sem cor, e isso é a decisão: a cor do dashboard mora no bloco do saldo. As
 * bandas pastel chegaram a entrar aqui e saíram — no escuro elas viram cinza com
 * sotaque, e no claro competiam com o adesivo logo acima.
 */
@Composable
private fun Bloco(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = Subheading, color = Tema.ink)
        Cartao(Modifier.fillMaxWidth()) {
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
        Text(rotulo, style = Body, color = Tema.ink, modifier = Modifier.weight(1f))
        MoneyText(cents = cents, style = MoneyBody)
    }
}

private fun variacaoEmPalavras(deltaCents: Long): String = when {
    deltaCents > 0 -> "Sobrou mais que no mês anterior"
    deltaCents < 0 -> "Sobrou menos que no mês anterior"
    else -> "Igual ao mês anterior"
}
