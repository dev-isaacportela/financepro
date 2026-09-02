package app.financepro.feature.txn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.component.Chips
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Formas
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.Filtro

/**
 * Os filtros de REQ-TXN-012, numa folha.
 *
 * Folha e não tela cheia: filtrar é ajuste sobre a lista, e mandar quem quer
 * conferir um gasto para outro destino tiraria a lista de vista justamente
 * enquanto se decide o que procurar.
 *
 * Contas **arquivadas continuam aqui**. REQ-ACC-005 tira a conta arquivada das
 * listas de seleção e do saldo total e manda preservar o histórico — um extrato
 * que esconde o passado da conta arquivada é o oposto do que ele pede.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnFilterSheet(
    state: TransactionsState,
    onFiltro: (Filtro) -> Unit,
    onTodoOPeriodo: (Boolean) -> Unit,
    onLimpar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filtro = state.filtro

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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = filtro.busca,
                onValueChange = { onFiltro(filtro.copy(busca = it)) },
                // Um campo só para os dois: quem procura digita `Padaria` ou
                // `18,50` no mesmo lugar. Separar em dois campos seria pedir
                // que a pessoa classificasse a própria busca antes de fazê-la.
                label = { Text("Buscar por descrição ou valor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Rotulo("Período")
            Chips(
                itens = listOf(false to "Mês", true to "Tudo"),
                selecionado = state.periodoTodo,
                onClick = onTodoOPeriodo,
            )

            Escolhas(state = state, onFiltro = onFiltro)

            if (filtro.ativo) {
                GhostButton(text = "Limpar filtros", onClick = onLimpar, modifier = Modifier.fillMaxWidth())
            }
            FilledCta(text = "Ver lista", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Conta, categoria e tipo. O primeiro chip de cada fileira é o "sem filtro":
 * um `null` explícito na lista vale mais que um botão de limpar por fileira,
 * porque mostra o estado atual em vez de escondê-lo atrás de uma ação.
 */
@Composable
private fun Escolhas(state: TransactionsState, onFiltro: (Filtro) -> Unit) {
    val filtro = state.filtro

    Rotulo("Conta")
    Chips(
        itens = listOf<Pair<Long?, String>>(null to "Todas") + state.contas.map { it.id as Long? to it.name },
        selecionado = filtro.contaId,
        onClick = { onFiltro(filtro.copy(contaId = it)) },
    )

    Rotulo("Categoria")
    Chips(
        itens = listOf<Pair<Long?, String>>(null to "Todas") + state.categorias.map { it.id as Long? to it.name },
        selecionado = filtro.categoriaId,
        onClick = { onFiltro(filtro.copy(categoriaId = it)) },
    )

    Rotulo("Tipo")
    Chips(
        itens = listOf<Pair<TxnType?, String>>(null to "Todos") +
            TxnType.entries.map { it as TxnType? to tipoCurto(it) },
        selecionado = filtro.tipo,
        onClick = { onFiltro(filtro.copy(tipo = it)) },
    )
}

private fun tipoCurto(tipo: TxnType) = when (tipo) {
    TxnType.INCOME -> "Receita"
    TxnType.EXPENSE -> "Despesa"
    TxnType.TRANSFER -> "Transferência"
}
