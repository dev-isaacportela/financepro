package app.financepro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.SlushFab
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Label
import app.financepro.core.ui.theme.MoneyLg
import app.financepro.core.ui.theme.Slush

@Composable
fun HomeScreen(onNovoLancamento: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val saldo by vm.saldoCents.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SALDO TOTAL", style = Caption, color = Slush.ink)
            MoneyText(cents = saldo, style = MoneyLg)
        }
        SlushFab(
            onClick = onNovoLancamento,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            // Sem biblioteca de ícones: um "+" em Label diz a mesma coisa e não
            // acrescenta dependência para desenhar um sinal de mais.
            Text("+", style = Label)
        }
    }
}
