package app.financepro.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.component.Chips
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.component.SeletorDeCor
import app.financepro.core.ui.theme.Acentos
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.NomesDeAcento
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Tema
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.usecase.CARD_DAY_RANGE

/**
 * Formulário de conta. REQ-ACC-001 · REQ-ACC-002 · REQ-UI-003
 *
 * Os três campos de cartão aparecem **só** para `CREDIT_CARD` — mesma regra que
 * a folha de lançamento aplica aos seus campos condicionais, e pelo mesmo
 * motivo: campo que não se aplica ao que está sendo criado é ruído que compete
 * com o que importa.
 *
 * Dia de fechamento e vencimento saem de uma lista de 1 a 28, não de um campo
 * livre: 29, 30 e 31 não existem em fevereiro, e a spec limita a faixa na
 * coluna. Recusar depois o que a interface ofereceu é pior que não oferecer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    conta: Account,
    contas: List<Account>,
    erro: String?,
    onChange: (Account) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = conta.name,
                onValueChange = { onChange(conta.copy(name = it)) },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Rotulo("Tipo")
            Chips(
                itens = AccountType.entries.map { it to tipoCurto(it) },
                selecionado = conta.type,
                onClick = { onChange(conta.copy(type = it)) },
            )

            Rotulo("Cor")
            SeletorDeCor(selecionada = conta.colorArgb, onEscolher = { onChange(conta.copy(colorArgb = it)) })

            Rotulo("Saldo de abertura")
            MoneyField(
                cents = conta.initialBalanceCents,
                onCentsChange = { onChange(conta.copy(initialBalanceCents = it)) },
            )

            if (conta.isCard) CamposDeCartao(conta, contas, onChange)

            if (erro != null) {
                Text("⚠ $erro", style = Caption, color = Tema.ink)
            }

            FilledCta(text = "Salvar", onClick = onSalvar, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** REQ-ACC-002 · REQ-CARD-001 — os quatro só existem para `CREDIT_CARD`. */
@Composable
private fun CamposDeCartao(conta: Account, contas: List<Account>, onChange: (Account) -> Unit) {
    Rotulo("Limite do cartão")
    MoneyField(
        cents = conta.creditLimitCents ?: 0,
        onCentsChange = { onChange(conta.copy(creditLimitCents = it)) },
    )

    Rotulo("Fecha no dia")
    Chips(
        itens = DIAS.map { it to it.toString() },
        selecionado = conta.closingDay,
        onClick = { onChange(conta.copy(closingDay = it)) },
    )

    Rotulo("Vence no dia")
    Chips(
        itens = DIAS.map { it to it.toString() },
        selecionado = conta.dueDay,
        onClick = { onChange(conta.copy(dueDay = it)) },
    )

    // REQ-CARD-001 — a conta que quita a fatura por padrão. A T-022 deixou a
    // coluna sem tela de propósito, porque escolher quem paga só tem sentido
    // onde a fatura é paga; a T-025 é essa tela, e este é o campo que ela usa.
    //
    // Outro cartão não paga fatura, e a própria conta em edição também não.
    Rotulo("Conta de pagamento")
    Chips(
        itens = contas
            .filter { !it.isCard && !it.archived && it.id != conta.id }
            .map { it.id to it.name },
        selecionado = conta.paymentAccountId,
        onClick = { onChange(conta.copy(paymentAccountId = it)) },
    )
}

private fun tipoCurto(tipo: AccountType) = when (tipo) {
    AccountType.CHECKING -> "Corrente"
    AccountType.SAVINGS -> "Poupança"
    AccountType.CASH -> "Dinheiro"
    AccountType.CREDIT_CARD -> "Cartão"
    AccountType.INVESTMENT -> "Investimento"
}

/**
 * A mesma faixa que `validateAccount` recusa fora (REQ-CARD-002), e não uma
 * cópia dela: a tela não pode oferecer um dia que a regra rejeita depois.
 */
private val DIAS = CARD_DAY_RANGE.toList()
