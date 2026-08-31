package app.financepro.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.theme.BodyLg
import app.financepro.core.ui.theme.DisplayXl
import app.financepro.core.ui.theme.Slush

/**
 * Onboarding. REQ-UI-005 · REQ-DS-009
 *
 * **Uma tela, uma pergunta.** Quem instala um app de finanças quer registrar um
 * gasto, não preencher cadastro — o app precisa ser utilizável em menos de 30
 * segundos, e cada campo a mais aqui é uma chance a mais de desistir.
 *
 * As duas contas saem prontas: `CASH` zerada e `CHECKING` com o valor informado.
 * O resto do cadastro é da T-015, quando o usuário quiser.
 *
 * É o único **pôster completo** do app (REQ-DS-009). A fita 3D está diferida,
 * então o pôster é tipografia — `DisplayXl` a 88sp, que em 360dp ocupa quase a
 * largura toda e é exatamente o efeito escultural que a fita reforçaria.
 */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    var saldo by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // O papel é pintado aqui, não herdado da janela: tela que depende do
            // `windowBackground` fica à mercê de um XML que não conhece o tema.
            .background(Slush.paper)
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        // Sem altura fixa: a 200% de fonte o bloco cresce em vez de truncar, e
        // display type que não cabe quebra, nunca vira reticências (REQ-DS-005).
        Text("QUANTO VOCÊ TEM HOJE?", style = DisplayXl, color = Slush.ink)
        Text(
            "O saldo da sua conta agora. Dá para ajustar depois.",
            style = BodyLg,
            color = Slush.ink,
        )
        MoneyField(cents = saldo, onCentsChange = { saldo = it })
        FilledCta(
            text = "Começar",
            onClick = { vm.concluir(saldo) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
