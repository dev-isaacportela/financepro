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
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.EscopoDeParcela
import app.financepro.domain.usecase.INSTALLMENT_RANGE
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
 *
 * [txnId] não nulo abre a **mesma** folha com a transação carregada (T-050,
 * REQ-TXN-001). Uma segunda folha só para editar teria os mesmos campos e
 * divergiria do original no primeiro campo novo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    onDismiss: () -> Unit,
    txnId: Long? = null,
    vm: QuickEntryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // O ramo nulo é o que impede o pior defeito desta task: o ViewModel é o da
    // Activity e sobrevive à folha, então uma edição dispensada sem salvar
    // deixaria `original` ligado — e o próximo "+" abriria preenchido, gravando
    // por cima da transação editada em vez de criar outra.
    //
    // Limpar na **abertura**, e não ao dispensar, também vence a corrida: a carga
    // é assíncrona, e dispensar antes de ela terminar sujaria o estado depois da
    // limpeza.
    LaunchedEffect(txnId) {
        if (txnId == null) vm.concluido() else vm.editar(txnId)
    }

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
        shape = Formas.extraLarge,
        containerColor = Tema.paper,
        contentColor = Tema.ink,
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
        // Título só na edição: criar é o caminho de três toques do Art. 18, e uma
        // linha a mais entre o topo e o valor é uma linha no caminho dele. Editar
        // não tem essa pressa, e sem o título nada distingue os dois modos para
        // quem ouve a tela (REQ-A11Y-001).
        if (state.editando) Text("Editar lançamento", style = Subheading, color = Tema.ink)

        // O único lugar com foco automático: Art. 18 protege este caminho.
        MoneyField(cents = state.cents, onCentsChange = vm::valor, autoFocus = true)
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

        if (state.mostraCategoria) Categorias(state, vm)

        if (state.mostraParcelas) {
            Rotulo("Parcelas")
            Chips(
                itens = PARCELAS.map { it to "${it}x" },
                selecionado = state.parcelas,
                onClick = vm::parcelas,
            )
        }

        // REQ-TXN-009 — editar uma parcela **pergunta** o escopo. Sem a pergunta
        // a T-050 abria a folha somente leitura, porque salvar uma parcela
        // sozinha deixaria as outras onze inconsistentes.
        if (state.ehParcela) Escopo(state, vm::escopo)

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
private fun Rotulo(texto: String) = Text(texto, style = Caption, color = Tema.ink)

/**
 * A validação devolve **todos** os erros de uma vez (T-007), então cada campo
 * mostra o seu e o usuário corrige numa passada.
 */
@Composable
private fun Erro(mensagem: String?) {
    if (mensagem != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠ $mensagem", style = Caption, color = Tema.ink)
        }
    }
}

/** O grid ordenado por uso (REQ-CAT-006), e o erro do campo logo abaixo. */
@Composable
private fun Categorias(state: QuickEntryState, vm: QuickEntryViewModel) {
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

/**
 * REQ-TXN-009 — a que parcelas a edição se aplica.
 *
 * O rótulo diz **qual** parcela está aberta ("Parcela 3 de 12"), porque a
 * pergunta só faz sentido sabendo de onde ela parte.
 */
@Composable
private fun Escopo(state: QuickEntryState, onEscopo: (EscopoDeParcela) -> Unit) {
    val txn = state.original
    val indice = txn?.installmentIndex
    val total = txn?.installmentTotal
    Rotulo(if (indice != null && total != null) "Parcela $indice de $total · aplicar a" else "Aplicar a")
    Chips(
        itens = EscopoDeParcela.entries.map { it to rotuloDoEscopo(it) },
        selecionado = state.escopo,
        onClick = onEscopo,
    )
}

/**
 * REQ-TXN-007 — a faixa inteira que a spec manda aceitar, e a **mesma**
 * `INSTALLMENT_RANGE` que `splitInstallments` valida: a tela não pode oferecer
 * um número que a divisão recusa.
 *
 * Todos os números, e não uma seleção de "comuns": é um `LazyRow`, então setenta
 * e dois chips custam o mesmo que sete, e curar a lista abriria a pergunta de
 * por que 13 não está lá.
 */
private val PARCELAS = INSTALLMENT_RANGE.toList()

private fun rotuloDoEscopo(escopo: EscopoDeParcela) = when (escopo) {
    EscopoDeParcela.SO_ESTA -> "Só esta"
    EscopoDeParcela.ESTA_E_FUTURAS -> "Esta e as futuras"
    EscopoDeParcela.TODAS -> "Todas"
}
