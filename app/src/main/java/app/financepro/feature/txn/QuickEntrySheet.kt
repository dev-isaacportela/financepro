package app.financepro.feature.txn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.CategorySticker
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.ValidationError

/**
 * Lançamento rápido. REQ-UI-002 · REQ-UI-003 · REQ-CAT-006 · Art. 18
 *
 * O fluxo mais importante do app, e o único protegido por artigo próprio: da
 * tela inicial até a despesa gravada em **três toques** — valor, categoria,
 * salvar — com o teclado numérico já aberto.
 *
 * Folha, nunca tela cheia. Tela cheia empilha um destino, tira o contexto de
 * baixo dos olhos e devolve o usuário a um lugar diferente de onde ele estava;
 * a folha some e o dashboard continua ali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    onDismiss: () -> Unit,
    vm: QuickEntryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.salvo) {
        if (state.salvo) {
            // Consumir antes de fechar: o ViewModel é da Activity e sobrevive à
            // folha, então um `salvo` que fica ligado impede a próxima abertura.
            vm.concluido()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = SlushShapes.extraLarge,
        containerColor = Slush.paper,
        contentColor = Slush.ink,
        tonalElevation = 0.dp,
    ) {
        Formulario(state, vm)
    }
}

/**
 * O formulário, separado da folha só porque as duas coisas mudam por motivos
 * diferentes: a folha é apresentação (forma, cor, dispensa) e isto aqui é a
 * ordem dos campos, que muda toda vez que um requisito de REQ-UI-003 muda.
 */
@Composable
private fun Formulario(state: QuickEntryState, vm: QuickEntryViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // `imePadding` é o que mantém o botão salvar acima do teclado.
            // Sem ele o terceiro toque do fluxo de três fica atrás das teclas.
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MoneyField(cents = state.cents, onCentsChange = vm::valor)
        Erro(state.erroDe(ValidationError.Campo.VALOR))

        Chips(
            itens = TxnType.entries.map { it to rotulo(it) },
            selecionado = state.tipo,
            onClick = vm::tipo,
        )

        Rotulo("Conta")
        Chips(
            itens = state.contas.map { it.id to it.name },
            selecionado = state.contaId,
            onClick = vm::conta,
        )
        Erro(state.erroDe(ValidationError.Campo.CONTA))

        if (state.mostraDestino) {
            Rotulo("Para")
            Chips(
                // A origem não pode ser destino de si mesma (REQ-TXN-004);
                // tirá-la da lista evita oferecer o erro antes de recusá-lo.
                itens = state.contas.filter { it.id != state.contaId }.map { it.id to it.name },
                selecionado = state.destinoId,
                onClick = vm::destino,
            )
            Erro(state.erroDe(ValidationError.Campo.CONTA_DESTINO))
        }

        if (state.mostraCategoria) {
            Rotulo("Categoria")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.categorias, key = { it.id }) { categoria ->
                    CategorySticker(
                        category = categoria,
                        selecionado = categoria.id == state.categoriaId,
                        onClick = { vm.categoria(categoria.id) },
                    )
                }
            }
            Erro(state.erroDe(ValidationError.Campo.CATEGORIA))
        }

        if (state.mostraParcelas) {
            Rotulo("Parcelas")
            Chips(
                itens = PARCELAS_COMUNS.map { it to "${it}x" },
                selecionado = state.parcelas,
                onClick = vm::parcelas,
            )
        }

        FilledCta(text = "Salvar", onClick = { vm.salvar() }, modifier = Modifier.fillMaxWidth())
    }
}

private fun rotulo(tipo: TxnType) = when (tipo) {
    TxnType.EXPENSE -> "Despesa"
    TxnType.INCOME -> "Receita"
    TxnType.TRANSFER -> "Transferência"
}

/**
 * Fileira de pílulas. Selecionada é preenchida, não colorida — mesma gramática
 * da barra de navegação, e cor sozinha não carrega estado (REQ-A11Y-003).
 */
@Composable
private fun <T> Chips(itens: List<Pair<T, String>>, selecionado: T?, onClick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(itens, key = { it.first.toString() }) { (valor, texto) ->
            if (valor == selecionado) {
                FilledCta(text = texto, onClick = { onClick(valor) })
            } else {
                GhostButton(text = texto, onClick = { onClick(valor) })
            }
        }
    }
}

@Composable
private fun Rotulo(texto: String) = Text(texto, style = Caption, color = Slush.ink)

/**
 * A validação devolve **todos** os erros de uma vez (T-007), então cada campo
 * mostra o seu e o usuário corrige numa passada.
 */
@Composable
private fun Erro(mensagem: String?) {
    if (mensagem != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠ $mensagem", style = Caption, color = Slush.ink)
        }
    }
}

/** 1x até 12x cobre quase toda compra parcelada; o resto é da T-027. */
private val PARCELAS_COMUNS = listOf(1, 2, 3, 4, 6, 10, 12)
