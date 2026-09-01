package app.financepro.feature.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.GrupoDeCategoria
import app.financepro.domain.usecase.Invoice
import app.financepro.domain.usecase.InvoiceStatus
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A fatura de um cartão. REQ-CARD-005 · REQ-CARD-006 · REQ-CARD-007 · REQ-CARD-008
 *
 * Não existe tabela `invoice` (ADR-004), então esta tela não "carrega uma
 * fatura": ela escolhe um mês e o domínio compõe. Trocar de mês é trocar um
 * `YearMonth`, não buscar outra linha.
 *
 * Itens por categoria, e não por data: numa fatura o que se procura é onde o
 * dinheiro foi, não quando. A ordem cronológica já existe na lista de
 * transações, filtrada por conta.
 */
@Composable
fun CardScreen(vm: CardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cartao = state.cartao
    val fatura = state.fatura

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(cartao?.name ?: "Cartão", style = Subheading, color = Slush.ink)

        Cabecalho(mes = state.mes, onAnterior = vm::mesAnterior, onSeguinte = vm::mesSeguinte)

        if (cartao == null || fatura == null) {
            Text("Cartão não encontrado.", style = Body, color = Slush.ink)
            return@Column
        }

        Resumo(fatura = fatura, limiteCents = state.limiteDisponivelCents)

        if (fatura.items.isEmpty()) {
            // REQ-UI-006 — o vazio diz o que é. Aqui não há ação a oferecer:
            // fatura sem compra é um bom estado, não um formulário por preencher.
            Text("Nenhuma compra nesta fatura.", style = Body, color = Slush.ink)
        } else {
            state.grupos.forEach { grupo ->
                Grupo(grupo = grupo, categoria = state.categoriaDe(grupo.categoriaId))
            }
        }

        Pagamento(state = state, onPagar = vm::abrirPagamento)
    }

    if (state.pagando != null) {
        FolhaDePagamento(
            state = state,
            onValor = vm::valorDoPagamento,
            onConfirmar = vm::pagar,
            onDismiss = vm::fecharPagamento,
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

@Composable
private fun Resumo(fatura: Invoice, limiteCents: Long?) {
    SlushCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Valor(rotulo = "Total da fatura", cents = fatura.totalCents, forte = true)
            // REQ-A11Y-003 — a situação vai **escrita**, não sinalizada por cor.
            // "Fechada" e "Paga" em dois tons da mesma tipografia seriam a mesma
            // coisa para quem não distingue os tons.
            Linha(rotulo = "Situação", texto = rotuloDe(fatura.status))
            Linha(rotulo = "Vence em", texto = DIA.format(fatura.dueDate))
            if (fatura.paidCents != 0L) {
                Valor(rotulo = "Já pago", cents = fatura.paidCents)
                Valor(rotulo = "Falta", cents = fatura.restanteCents)
            }
            if (limiteCents != null) Valor(rotulo = "Limite disponível", cents = limiteCents)
        }
    }
}

@Composable
private fun Grupo(grupo: GrupoDeCategoria, categoria: Category?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Valor(rotulo = categoria?.name ?: "Sem categoria", cents = grupo.totalCents, forte = true)
        grupo.itens.forEach { ItemDaFatura(txn = it, categoria = categoria) }
    }
}

/**
 * Uma compra da fatura.
 *
 * `LinhaDeTransacao` não serve aqui, e a tela mostrou por quê: o subtítulo dela
 * é "categoria · conta", e dentro de um grupo que já leva o nome da categoria,
 * na tela do próprio cartão, isso imprime a mesma palavra três vezes. Sem
 * passar a categoria era pior — a linha dizia "Sem categoria" **dentro** do
 * grupo "Compras", e a tela se contradizia.
 *
 * O que uma linha de fatura quer é a data e, quando existe, a parcela: é o que
 * o extrato do banco mostra, e é o que se procura ao conferir a fatura.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDaFatura(txn: Txn, categoria: Category?) {
    SlushCard(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = txn.description.ifBlank { categoria?.name ?: "Compra" },
                    style = BodyStrong,
                    color = Slush.ink,
                )
                Text(text = detalheDe(txn), style = Caption, color = Slush.ink)
            }
            MoneyText(cents = txn.amountCents)
        }
    }
}

/** Data e, quando for parcela, a posição — "3 de 12", como o banco escreve. */
private fun detalheDe(txn: Txn): String {
    val dia = DIA.format(txn.date)
    val indice = txn.installmentIndex
    val total = txn.installmentTotal
    return if (indice != null && total != null) "$dia · parcela $indice de $total" else dia
}

/**
 * REQ-CARD-006 — pagar exige saber de onde sai o dinheiro.
 *
 * Sem `paymentAccountId`, a tela diz **qual** configuração falta e onde ela
 * mora, em vez de um botão desabilitado que não explica nada.
 */
@Composable
private fun Pagamento(state: CardState, onPagar: () -> Unit) {
    val conta = state.contaDePagamento
    when {
        conta == null -> Text(
            "Escolha a conta de pagamento nos dados do cartão, em Contas.",
            style = Body,
            color = Slush.ink,
        )

        // Pelo que falta, e não pelo status: uma fatura ainda **aberta** pode já
        // estar quitada, e oferecer "pagar" ali abriria a folha em R$ 0,00.
        state.fatura?.restanteCents == 0L ->
            Text("Fatura paga.", style = Body, color = Slush.ink)

        else -> {
            Text("Sai de " + conta.name, style = Caption, color = Slush.ink)
            FilledCta(text = "Pagar fatura", onClick = onPagar, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** REQ-CARD-006 — o valor vem pronto e **editável**: parcial é caso comum. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDePagamento(
    state: CardState,
    onValor: (Long) -> Unit,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            Text("Pagar fatura", style = Subheading, color = Slush.ink)
            MoneyField(cents = state.pagando ?: 0, onCentsChange = onValor, autoFocus = true)
            state.contaDePagamento?.let { Rotulo("Sai de " + it.name) }
            state.fatura?.let { Rotulo("Entra no cartão em " + DIA.format(it.dueDate)) }
            FilledCta(text = "Confirmar", onClick = onConfirmar, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Rótulo e valor. O `weight` vai no **rótulo**, nunca no valor: num `Row` os
 * filhos sem peso são medidos primeiro, e com a fonte a 200% o rótulo engoliria
 * a linha e o valor desceria caractere a caractere (REQ-A11Y-004, T-020).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Valor(rotulo: String, cents: Long, forte: Boolean = false) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rotulo,
            style = if (forte) BodyStrong else Body,
            color = Slush.ink,
            modifier = Modifier.weight(1f),
        )
        MoneyText(cents = cents, style = MoneyCaption)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Linha(rotulo: String, texto: String) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, style = Body, color = Slush.ink, modifier = Modifier.weight(1f))
        Text(texto, style = BodyStrong, color = Slush.ink)
    }
}

private fun rotuloDe(status: InvoiceStatus) = when (status) {
    InvoiceStatus.ABERTA -> "Aberta"
    InvoiceStatus.FECHADA -> "Fechada"
    InvoiceStatus.PAGA -> "Paga"
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MES: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR)
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", PT_BR)
