package app.financepro.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.EstadoVazio
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.LightBlue
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType

/**
 * Lista de contas com saldo. REQ-ACC-001 · REQ-ACC-005 · REQ-UI-006
 *
 * Arquivadas ficam **escondidas atrás de um botão**, não excluídas: REQ-ACC-005
 * exige que saiam das listas e do saldo total preservando o histórico, e um
 * botão de excluir ali levaria as transações junto por `CASCADE`.
 */
@Composable
fun AccountsScreen(vm: AccountsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Tema.paper).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Contas", style = Subheading, color = Tema.ink)
            GhostButton(text = "Nova", onClick = vm::nova)
        }

        if (state.visiveis.isEmpty() && state.carregado) {
            // REQ-UI-006: estado vazio traz a ação que o preenche.
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EstadoVazio(titulo = "SEM CONTAS AINDA", sticker = LightBlue)
                FilledCta(text = "Criar a primeira", onClick = vm::nova)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.visiveis, key = { it.id }) { conta ->
                Linha(
                    // Arquivar uma conta tira a linha da lista; sem isto as de
                    // baixo saltam para o lugar dela no mesmo quadro.
                    modifier = Modifier.animateItem(),
                    conta = conta,
                    saldoCents = state.saldos[conta.id] ?: 0,
                    onClick = { vm.editar(conta) },
                    onArquivar = { vm.arquivar(conta) },
                )
            }
            if (state.arquivadas > 0) {
                item {
                    GhostButton(
                        text = if (state.mostrarArquivadas) {
                            "Esconder arquivadas"
                        } else {
                            "Ver ${state.arquivadas} arquivadas"
                        },
                        onClick = vm::alternarArquivadas,
                    )
                }
            }
        }
    }

    state.editando?.let { conta ->
        AccountFormSheet(
            conta = conta,
            contas = state.contas,
            erro = state.erro,
            onChange = vm::alterar,
            onSalvar = vm::salvar,
            onDismiss = vm::fechar,
        )
    }
}

@Composable
private fun Linha(
    conta: Account,
    saldoCents: Long,
    onClick: () -> Unit,
    onArquivar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Cartao(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        // `FlowRow` e não `Row`: com a fonte a 200% o valor e o botão não cabem
        // ao lado do nome, e num Row eles são medidos primeiro — a coluna do
        // nome sobrava com um caractere de largura e o nome descia letra por
        // letra ("( D i n h e i r o"). Aqui o que não couber cai para a linha
        // de baixo, e na escala normal nada muda (REQ-A11Y-004).
        FlowRow(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PontoDeCor(conta.colorArgb)
            Column(Modifier.weight(1f)) {
                Text(conta.name, style = Body, color = Tema.ink, maxLines = 1)
                Text(
                    text = rotulo(conta),
                    style = Caption,
                    color = Tema.ink,
                )
            }
            MoneyText(cents = saldoCents)
            GhostButton(
                text = if (conta.archived) "Restaurar" else "Arquivar",
                onClick = onArquivar,
            )
        }
    }
}

/**
 * Ponto de cor, não bloco: a lista é densa e a cor aqui é identidade, não estado.
 *
 * O anel não é decoração, é o mesmo motivo de design.md §6.3: sobre a superfície
 * clara, um ponto Verde-azulado dá 2.77:1 e um Laranja 2.53:1 — abaixo dos 3:1
 * de elemento não textual, e leria como falha de renderização, não como escolha.
 */
@Composable
private fun PontoDeCor(colorArgb: Int) = Box(
    Modifier
        .size(16.dp)
        .clip(Formas.extraSmall)
        .background(Color(colorArgb))
        .border(OutlineWidth, Tema.ink, Formas.extraSmall),
)

private fun rotulo(conta: Account): String {
    val tipo = tipoLegivel(conta)
    return if (conta.archived) "$tipo · arquivada" else tipo
}

private fun tipoLegivel(conta: Account) = when (conta.type) {
    AccountType.CHECKING -> "Conta corrente"
    AccountType.SAVINGS -> "Poupança"
    AccountType.CASH -> "Dinheiro"
    AccountType.CREDIT_CARD -> "Cartão de crédito"
    AccountType.INVESTMENT -> "Investimento"
}
