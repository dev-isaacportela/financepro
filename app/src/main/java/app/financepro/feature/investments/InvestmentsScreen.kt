package app.financepro.feature.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.R
import app.financepro.core.money.formatBRL
import app.financepro.core.money.parseCents
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.component.EstadoVazio
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.LightGreen
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.MoneyLg
import app.financepro.core.ui.theme.Subheading
import app.financepro.core.ui.theme.Tema
import app.financepro.domain.model.Indexador
import app.financepro.domain.usecase.MesDeInvestimento
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Investimentos e rendimento. REQ-INV-002 · REQ-INV-003 · REQ-INV-004 · REQ-INV-006
 *
 * A tela responde três perguntas, nesta ordem: quanto eu tenho, quanto rendeu
 * neste mês, e como isso vem andando. O gráfico é a terceira, e **nunca sozinho**
 * — a lista de meses logo abaixo diz os mesmos números em texto, que é o que faz
 * o Art. 17 valer para quem usa leitor de tela ou não distingue a linha do fundo.
 */
@Composable
fun InvestmentsScreen(vm: InvestmentsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cabecalho(state.mes, vm::mesAnterior, vm::mesSeguinte)

        FaixaDoCdi(state, vm)

        if (state.vazio && state.carregado) {
            EstadoVazio(
                titulo = "SEM INVESTIMENTOS",
                sticker = LightGreen,
                icone = R.drawable.ic_cat_cash,
                descricao = "Crie uma conta do tipo Investimento em Mais · Contas.",
            )
        } else {
            Totais(state)
            state.linhas.forEach { linha ->
                CartaoDoInvestimento(linha, state.mes, onLancar = { vm.lancar(linha) })
            }
            Evolucao(state.serie)
        }

        if (state.erro != null) {
            Text("⚠ " + state.erro, style = Caption, color = Tema.ink)
        }
    }

    state.lancando?.let { lancamento ->
        FolhaDeRendimento(
            lancamento = lancamento,
            mes = state.mes,
            onChange = vm::alterarLancamento,
            onSalvar = vm::confirmarLancamento,
            onDismiss = vm::fecharLancamento,
        )
    }
}

/** Mesma gramática do cabeçalho de relatórios e do orçamento. */
@Composable
private fun Cabecalho(mes: YearMonth, onAnterior: () -> Unit, onSeguinte: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Investimentos", style = Subheading, color = Tema.ink)
        Text(
            text = MES.format(mes).replaceFirstChar { it.uppercase() },
            style = Body,
            color = Tema.inkMute,
            modifier = Modifier.padding(top = 4.dp),
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
 * O CDI, com a data **do dado**. REQ-INV-005 · REQ-INV-006
 *
 * A data não é enfeite: numa segunda-feira o valor mais recente é o de sexta, e
 * quem confere a taxa precisa saber de quando ela é. Sem valor nenhum, o lugar
 * do número é ocupado pelo campo manual — a tela nunca mostra um CDI que não
 * existe.
 */
@Composable
private fun FaixaDoCdi(state: InvestmentsState, vm: InvestmentsViewModel) {
    val cdi = state.cdi
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (cdi == null) "CDI ainda não obtido" else "CDI " + percentual(cdi.anualBp) + " a.a.",
                        style = BodyStrong,
                        color = Tema.ink,
                    )
                    Text(
                        text = when {
                            cdi == null -> "Toque em Atualizar, ou informe o valor"
                            cdi.manual -> "Informado por você em " + DIA.format(cdi.em)
                            else -> "Banco Central · " + DIA.format(cdi.em)
                        },
                        style = Caption,
                        color = Tema.inkMute,
                    )
                }
                GhostButton(
                    text = if (state.buscandoCdi) "Buscando…" else "Atualizar",
                    onClick = vm::atualizarCdi,
                    enabled = !state.buscandoCdi,
                )
            }

            if (state.editandoCdi) {
                CampoDeCdi(onInformar = vm::informarCdi)
            } else {
                GhostButton(text = "Informar à mão", onClick = { vm.editarCdi(true) })
            }
        }
    }
}

/** REQ-INV-006 — duas casas de por cento são pontos-base, como no formulário. */
@Composable
private fun CampoDeCdi(onInformar: (Int) -> Unit) {
    var texto by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            suffix = { Text("% a.a.", style = Caption) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        val bp = parseCents(texto)?.toInt()
        FilledCta(
            text = "Usar",
            onClick = { bp?.let(onInformar) },
            enabled = bp != null && bp > 0,
        )
    }
}

@Composable
private fun Totais(state: InvestmentsState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Total investido", style = Caption, color = Tema.inkMute)
        MoneyText(cents = state.totalCents, style = MoneyLg)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rendeu no mês", style = Caption, color = Tema.inkMute)
            MoneyText(cents = state.lancadoNoMesCents, style = MoneyCaption, porSinal = true)
        }
    }
}

/**
 * Um investimento. REQ-INV-002 · REQ-INV-003
 *
 * O botão só existe enquanto o mês não tem rendimento: depois de lançado, o
 * lugar dele é ocupado pelo valor. Não há caminho para lançar duas vezes o mesmo
 * mês, que é a forma mais fácil de inventar patrimônio nesta tela.
 */
@Composable
private fun CartaoDoInvestimento(linha: LinhaDeInvestimento, mes: YearMonth, onLancar: () -> Unit) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(linha.conta.name, style = BodyStrong, color = Tema.ink, modifier = Modifier.weight(1f))
                MoneyText(cents = linha.saldoCents, style = MoneyCaption)
            }

            Text(rotuloDaTaxa(linha), style = Caption, color = Tema.inkMute)

            if (linha.jaLancado) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lançado em " + MES_CURTO.format(mes), style = Caption, color = Tema.inkMute)
                    MoneyText(cents = linha.lancadoCents, style = MoneyCaption, porSinal = true)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Previsto", style = Caption, color = Tema.inkMute)
                        if (linha.previstoCents == null) {
                            // REQ-INV-002: sem taxa, travessão. Zero seria lido
                            // como "não rendeu", que é afirmação, não ausência.
                            Text("—", style = MoneyCaption, color = Tema.ink)
                        } else {
                            MoneyText(cents = linha.previstoCents, style = MoneyCaption)
                        }
                    }
                    GhostButton(
                        text = "Lançar",
                        onClick = onLancar,
                        enabled = linha.previstoCents != null,
                    )
                }
            }
        }
    }
}

/**
 * REQ-INV-004 — o patrimônio nos 12 meses, e os mesmos números em texto.
 *
 * A lista embaixo do gráfico **é** a alternativa textual: não é redundância a
 * cortar depois, é o requisito. Um `Canvas` não tem semântica nenhuma para o
 * leitor de tela.
 */
@Composable
private fun Evolucao(serie: List<MesDeInvestimento>) {
    if (serie.size < 2) return
    val tinta = Tema.ink
    val teto = serie.maxOf { it.saldoFimCents }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Últimos 12 meses", style = Subheading, color = Tema.ink)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(ALTURA_LINHA)
                .semantics {
                    contentDescription = "Evolução do patrimônio investido, de " +
                        MES_CURTO.format(serie.first().mes) + " a " + MES_CURTO.format(serie.last().mes) +
                        ", terminando em " + formatBRL(serie.last().saldoFimCents)
                },
        ) {
            desenharLinha(serie.map { it.saldoFimCents }, teto, tinta)
        }

        Text("Topo da escala " + formatBRL(teto), style = Caption, color = Tema.inkMute)

        serie.reversed().forEach { ponto ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = MES_CURTO.format(ponto.mes),
                    style = Caption,
                    color = Tema.inkMute,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(cents = ponto.rendimentoCents, style = MoneyCaption, porSinal = true)
                Spacer(Modifier.weight(0.2f))
                MoneyText(cents = ponto.saldoFimCents, style = MoneyCaption)
            }
        }
    }
}

/** REQ-INV-003 — o previsto entra preenchido, e sai como a pessoa quiser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDeRendimento(
    lancamento: Lancamento,
    mes: YearMonth,
    onChange: (Long) -> Unit,
    onSalvar: () -> Unit,
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
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Rendimento de " + MES.format(mes),
                style = Subheading,
                color = Tema.ink,
            )
            Text(
                text = lancamento.conta.name + " · confira no extrato e ajuste se precisar",
                style = Caption,
                color = Tema.inkMute,
            )

            MoneyField(cents = lancamento.cents, onCentsChange = onChange, autoFocus = true)

            FilledCta(text = "Lançar", onClick = onSalvar, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "110% do CDI · 16,39% a.a.", ou o que der para dizer com o que se tem. */
private fun rotuloDaTaxa(linha: LinhaDeInvestimento): String {
    val conta = linha.conta
    val taxa = conta.taxaBp
    if (conta.indexador == null || taxa == null) return "Sem taxa — edite a conta"
    val base = when (conta.indexador) {
        Indexador.PREFIXADO -> percentual(taxa) + " a.a."
        Indexador.CDI -> percentual(taxa) + " do CDI"
    }
    val efetiva = linha.anualBp
    return when {
        efetiva == null -> base + " · sem CDI, sem previsão"
        conta.indexador == Indexador.CDI -> base + " · " + percentual(efetiva) + " a.a."
        else -> base
    }
}

/** Pontos-base para texto: `1639` → `16,39%`. Sem ponto flutuante. */
private fun percentual(bp: Int): String {
    val inteiro = bp / 100
    val centesimos = (bp % 100).toString().padStart(2, '0')
    return if (bp % 100 == 0) "$inteiro%" else "$inteiro,$centesimos%"
}

/**
 * A linha do patrimônio, no precedente do gráfico de relatórios: `Path` e
 * `drawPath`, sem biblioteca de charts.
 *
 * `toFloat` aqui é legítimo — isto é coordenada de tela, não dinheiro, e
 * `feature/` não é caminho de dinheiro para o Art. 6.
 */
private fun DrawScope.desenharLinha(valores: List<Long>, teto: Long, tinta: Color) {
    val passo = size.width / (valores.size - 1)
    val caminho = Path()
    valores.forEachIndexed { i, valor ->
        val x = passo * i
        val y = size.height * (1f - valor.toFloat() / teto)
        if (i == 0) caminho.moveTo(x, y) else caminho.lineTo(x, y)
    }
    drawPath(path = caminho, color = tinta, style = Stroke(width = TRACO.toPx()))
}

private val ALTURA_LINHA = 140.dp
private val TRACO = 2.dp

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val MES_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM/yy", PT_BR)
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
