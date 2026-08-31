package app.financepro.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.SlushSurface
import app.financepro.core.ui.theme.Display
import app.financepro.core.ui.theme.Label
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.Subheading
import app.financepro.data.prefs.SecurityPrefs
import app.financepro.data.repo.AccountRepository
import app.financepro.feature.accounts.AccountsScreen
import app.financepro.feature.categories.CategoriesScreen
import app.financepro.feature.home.HomeScreen
import app.financepro.feature.lock.LockScreen
import app.financepro.feature.onboarding.OnboardingScreen
import app.financepro.feature.txn.QuickEntrySheet
import app.financepro.feature.txn.TransactionsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

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

// Destinos de dentro do "Mais". Não são abas: não cabem numa barra de quatro, e
// promovê-los tiraria espaço do que se usa todo dia.
@Serializable
data object Contas

@Serializable
data object Categorias

private val ABAS = listOf(
    Inicio to "Início",
    Transacoes to "Transações",
    Orcamento to "Orçamento",
    Mais to "Mais",
)

/**
 * "Já passou pelo onboarding?" é uma **pergunta ao banco**, não uma flag.
 *
 * Sem conta nenhuma, o app não tem onde lançar nada — então "sem contas" já é a
 * definição de primeiro uso. Uma flag em DataStore diria a mesma coisa com um
 * estado a mais para dessincronizar: apagar os dados e não a flag deixaria o
 * usuário preso numa tela inicial que não funciona.
 *
 * `null` enquanto a primeira leitura não chega, para não piscar o onboarding na
 * cara de quem já usa o app.
 */
@HiltViewModel
class RaizViewModel @Inject constructor(
    contas: AccountRepository,
    private val seguranca: SecurityPrefs,
) : ViewModel() {
    val precisaOnboarding = contas.observeAll()
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), null)

    /** `null` até a primeira leitura, pelo mesmo motivo de [precisaOnboarding]. */
    val bloqueio = seguranca.bloqueio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), null)

    private val _destrancado = MutableStateFlow(false)
    val destrancado = _destrancado.asStateFlow()

    fun destrancar() {
        _destrancado.value = true
    }

    fun alternarBloqueio() = viewModelScope.launch {
        seguranca.definirBloqueio(!seguranca.bloqueio.first())
    }

    private companion object {
        const val PARADA_MS = 5_000L
    }
}

/**
 * O bloqueio vem **antes** do onboarding no `when`, e não como sobreposição: o
 * requisito é "antes de exibir qualquer dado financeiro" (REQ-SEC-003), e uma
 * camada por cima do dashboard teria o dashboard montado atrás dela.
 *
 * ponytail: tranca uma vez por processo, não a cada volta do segundo plano. O
 * relock em `onStop` precisa distinguir "usuário saiu" de "a tela de credencial
 * do sistema subiu" — que é outra Activity, e sem essa distinção o app entra em
 * laço pedindo senha. Fechar isso quando houver aparelho para exercer os dois
 * caminhos.
 */
@Composable
fun FinanceNav(nav: NavHostController = rememberNavController(), vm: RaizViewModel = hiltViewModel()) {
    val precisaOnboarding by vm.precisaOnboarding.collectAsStateWithLifecycle()
    val bloqueio by vm.bloqueio.collectAsStateWithLifecycle()
    val destrancado by vm.destrancado.collectAsStateWithLifecycle()

    when {
        // Primeira leitura do banco e da preferência; nada a mostrar ainda.
        precisaOnboarding == null || bloqueio == null -> Unit
        bloqueio == true && !destrancado -> LockScreen(onDesbloqueado = vm::destrancar)
        precisaOnboarding == true -> OnboardingScreen()
        else -> Abas(nav, bloqueio == true, vm::alternarBloqueio)
    }
}

@Composable
private fun Abas(nav: NavHostController, bloqueio: Boolean, onAlternarBloqueio: () -> Unit) {
    var lancando by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Slush.paper,
        bottomBar = { BarraInferior(nav) },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = Inicio,
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            composable<Inicio> {
                HomeScreen(
                    onNovoLancamento = { lancando = true },
                    onVerContas = { nav.navigate(Contas) },
                    // `launchSingleTop`: "Ver todas" empilha a aba de propósito,
                    // para o voltar devolver o dashboard — mas dois toques
                    // seguidos não podem empilhar duas cópias dela.
                    onVerTransacoes = { nav.navigate(Transacoes) { launchSingleTop = true } },
                )
            }
            composable<Mais> {
                MaisScreen(
                    onIr = { nav.navigate(it) },
                    bloqueio = bloqueio,
                    onAlternarBloqueio = onAlternarBloqueio,
                )
            }
            composable<Contas> { AccountsScreen() }
            composable<Categorias> { CategoriesScreen() }
            composable<Transacoes> { TransactionsScreen(onNovoLancamento = { lancando = true }) }
            // A restante entra na sua task: T-029.
            emDesenvolvimento<Orcamento>("Orçamento")
        }
    }

    // A folha vive **fora** do NavHost de propósito: ela não é um destino, é uma
    // sobreposição. Como destino, o botão voltar a trataria como tela e o
    // dashboard sairia de baixo dela (REQ-UI-002).
    if (lancando) {
        QuickEntrySheet(onDismiss = { lancando = false })
    }
}

private inline fun <reified T : Any> NavGraphBuilder.emDesenvolvimento(titulo: String) =
    composable<T> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(titulo, style = Display, color = Slush.ink, textAlign = TextAlign.Center)
        }
    }

/**
 * O "Mais" da barra: um índice, não uma tela.
 *
 * Contas e Categorias são destinos próprios em vez de abas porque a barra tem
 * quatro lugares (REQ-UI-001) e nenhum deles deve ser gasto com algo que se
 * mexe uma vez por mês.
 */
@Composable
private fun MaisScreen(onIr: (Any) -> Unit, bloqueio: Boolean, onAlternarBloqueio: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Mais", style = Subheading, color = Slush.ink)
        GhostButton(text = "Contas", onClick = { onIr(Contas) }, modifier = Modifier.fillMaxWidth())
        GhostButton(
            text = "Categorias",
            onClick = { onIr(Categorias) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Uma preferência não é uma tela de ajustes. O estado vai **escrito** no
        // rótulo, não sinalizado por cor (REQ-A11Y-003) — e é o mesmo botão,
        // que também é o que o leitor de tela anuncia por inteiro.
        GhostButton(
            text = if (bloqueio) "Bloqueio do app: ligado" else "Bloqueio do app: desligado",
            onClick = onAlternarBloqueio,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BarraInferior(nav: NavHostController) {
    val atual by nav.currentBackStackEntryAsState()

    SlushSurface(
        // `windowInsetsPadding` antes do padding visual: com `enableEdgeToEdge` o
        // Scaffold entrega o slot colado na borda física, e quem tem três botões
        // de navegação em vez de gestos veria a barra atrás deles. No aparelho de
        // gestos o erro quase não aparece — foi preciso rodar para enxergar.
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
