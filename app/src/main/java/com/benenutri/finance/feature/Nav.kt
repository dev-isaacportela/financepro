package com.benenutri.finance.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraphBuilder
import com.benenutri.finance.core.ui.component.SlushSurface
import com.benenutri.finance.core.ui.theme.Display
import com.benenutri.finance.core.ui.theme.Label
import com.benenutri.finance.core.ui.theme.OutlineWidth
import com.benenutri.finance.core.ui.theme.Pill
import com.benenutri.finance.core.ui.theme.Slush
import kotlinx.serialization.Serializable

/**
 * Navegação principal. REQ-UI-001 ·
 * [design.md](../../../../../../../../docs/design.md) §6.1
 *
 * Rotas type-safe: o destino **é** o objeto, não uma string. Rota como texto só
 * quebra em tempo de execução, e num app de quatro abas o custo de descobrir
 * isso já é maior que o de anotar quatro `data object`.
 *
 * A barra é uma pílula flutuante contornada, não a `NavigationBar` do Material —
 * aquela traz superfície tonal e nenhum contorno, que é o oposto da gramática de
 * Slush. Seleção é sinalizada por **preenchimento**, não só por cor (REQ-A11Y-003).
 */

@Serializable
data object Inicio

@Serializable
data object Transacoes

@Serializable
data object Orcamento

@Serializable
data object Mais

private val ABAS = listOf(
    Inicio to "Início",
    Transacoes to "Transações",
    Orcamento to "Orçamento",
    Mais to "Mais",
)

@Composable
fun FinanceNav(nav: NavHostController = rememberNavController()) {
    Scaffold(
        containerColor = Slush.paper,
        bottomBar = { BarraInferior(nav) },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = Inicio,
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            // Cada tela real entra aqui na sua task: T-017 Início,
            // T-014 Transações, T-029 Orçamento, T-015/T-016/T-018 Mais.
            emDesenvolvimento<Inicio>("Início")
            emDesenvolvimento<Transacoes>("Transações")
            emDesenvolvimento<Orcamento>("Orçamento")
            emDesenvolvimento<Mais>("Mais")
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.emDesenvolvimento(titulo: String) =
    composable<T> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(titulo, style = Display, color = Slush.ink, textAlign = TextAlign.Center)
        }
    }

@Composable
private fun BarraInferior(nav: NavHostController) {
    val atual by nav.currentBackStackEntryAsState()

    SlushSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = Pill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ABAS.forEach { (destino, rotulo) ->
                val selecionada = atual?.destination?.hasRoute(destino::class) == true
                Aba(
                    rotulo = rotulo,
                    selecionada = selecionada,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // `launchSingleTop` e o popUpTo na raiz mantêm a pilha em
                        // uma tela por aba: sem eles, ir e voltar entre abas
                        // empilha destinos e o botão voltar vira um labirinto.
                        nav.navigate(destino) {
                            popUpTo(Inicio) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Aba(
    rotulo: String,
    selecionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fundo = if (selecionada) Slush.ink else Slush.paper
    val tinta = if (selecionada) Slush.paper else Slush.ink

    Surface(
        onClick = onClick,
        modifier = modifier.semantics { selected = selecionada },
        shape = Pill,
        color = fundo,
        contentColor = tinta,
        border = if (selecionada) null else BorderStroke(OutlineWidth, Slush.ink),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = rotulo,
            style = Label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
        )
    }
}
