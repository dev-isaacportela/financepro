package app.financepro.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.SlushSurface
import app.financepro.core.ui.theme.Label
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.Subheading
import app.financepro.data.prefs.SecurityPrefs
import app.financepro.data.repo.AccountRepository
import app.financepro.feature.accounts.AccountsScreen
import app.financepro.feature.budget.BudgetScreen
import app.financepro.feature.card.CardScreen
import app.financepro.feature.categories.CategoriesScreen
import app.financepro.feature.export.ExportScreen
import app.financepro.feature.home.HomeScreen
import app.financepro.feature.lock.LockScreen
import app.financepro.feature.onboarding.OnboardingScreen
import app.financepro.feature.recurring.RecurringScreen
import app.financepro.feature.reports.ReportsScreen
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
import java.time.YearMonth
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

/**
 * A lista, opcionalmente já filtrada. REQ-RPT-004
 *
 * Os dois argumentos têm valor **neutro** em vez de serem nulos: a rota
 * type-safe do Navigation resolve `Long?` para um `NavType` que não existe, e o
 * erro só apareceria ao navegar. Zero é "sem filtro de categoria" e vazio é
 * "mês corrente" — a aba passa `Transacoes()` e não sabe da existência deles.
 */
@Serializable
data class Transacoes(val categoriaId: Long = 0, val mesIso: String = "")

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

@Serializable
data object Recorrencias

@Serializable
data object Relatorios

@Serializable
data object Exportar

/**
 * A fatura de um cartão. O único destino com argumento — e mesmo aqui o id vai
 * no objeto, não numa string interpolada: o `CardViewModel` o lê de volta com
 * `toRoute`, e um erro de nome vira erro de compilação em vez de tela em branco.
 */
@Serializable
data class Cartao(val id: Long)

private val ABAS = listOf(
    Inicio to "Início",
    Transacoes() to "Transações",
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
    var editandoId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = Slush.paper,
        bottomBar = { BarraInferior(nav) },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = Inicio,
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            rotas(
                nav = nav,
                bloqueio = bloqueio,
                onAlternarBloqueio = onAlternarBloqueio,
                onLancar = { lancando = true },
                onEditar = { editandoId = it },
            )
        }
    }

    // A folha vive **fora** do NavHost de propósito: ela não é um destino, é uma
    // sobreposição. Como destino, o botão voltar a trataria como tela e o
    // dashboard sairia de baixo dela (REQ-UI-002).
    // A mesma folha para criar e para editar (T-050): os campos são os mesmos, e
    // duas folhas divergiriam no primeiro campo novo.
    if (lancando || editandoId != null) {
        QuickEntrySheet(
            txnId = editandoId,
            onDismiss = {
                lancando = false
                editandoId = null
            },
        )
    }
}

/**
 * Os destinos, separados do `Scaffold` porque são coisas diferentes: ali está a
 * moldura — barra, cores, insets — e aqui, o mapa. A lista cresce a cada tela
 * nova, e a moldura não muda desde a T-011.
 *
 * `onLancar` e `onEditar` sobem como parâmetro em vez de o estado descer:
 * a folha de lançamento é sobreposição, não destino, e quem a segura é [Abas].
 */
private fun NavGraphBuilder.rotas(
    nav: NavHostController,
    bloqueio: Boolean,
    onAlternarBloqueio: () -> Unit,
    onLancar: () -> Unit,
    onEditar: (Long) -> Unit,
) {
    composable<Inicio> {
        HomeScreen(
            onNovoLancamento = onLancar,
            onVerContas = { nav.navigate(Contas) },
            // `launchSingleTop`: "Ver todas" empilha a aba de propósito, para o
            // voltar devolver o dashboard — mas dois toques seguidos não podem
            // empilhar duas cópias dela.
            onVerTransacoes = { nav.navigate(Transacoes()) { launchSingleTop = true } },
            onEditar = onEditar,
            onVerCartao = { nav.navigate(Cartao(it)) },
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
    composable<Recorrencias> { RecurringScreen() }
    composable<Exportar> { ExportScreen() }
    composable<Relatorios> {
        ReportsScreen(
            // REQ-RPT-004 — a fatia leva à lista já filtrada por categoria **e**
            // período. Sem o mês, o toque numa fatia de março abriria a lista em
            // setembro, filtrada por uma categoria que talvez nem apareça lá.
            onVerCategoria = { categoria, mes ->
                // As mesmas regras da barra inferior, e não um `navigate` seco:
                // `Transacoes` **é** uma aba, e empilhá-la aqui deixaria o
                // destino "Mais" restaurando a lista de transações — a barra
                // apontando um lugar e a tela mostrando outro. Sem
                // `restoreState`: o que se quer é a lista com **este** filtro,
                // não a que ficou salva de antes.
                nav.navigate(Transacoes(categoria, mes.toString())) {
                    popUpTo(Inicio) { saveState = true }
                    launchSingleTop = true
                }
            },
            onEditar = onEditar,
        )
    }
    composable<Cartao> { CardScreen() }
    composable<Transacoes> {
        TransactionsScreen(onNovoLancamento = onLancar, onEditar = onEditar)
    }
    composable<Orcamento> { BudgetScreen() }
}

/**
 * O "Mais" da barra: um índice, não uma tela.
 *
 * Contas, Categorias, Recorrências e Relatórios são destinos próprios em vez de
 * abas porque a barra tem quatro lugares (REQ-UI-001) e nenhum deles deve ser
 * gasto com algo que se mexe uma vez por mês. O que a recorrência produz
 * aparece onde importa: o bloco "Próximas contas" do dashboard.
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
        GhostButton(
            text = "Recorrências",
            onClick = { onIr(Recorrencias) },
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton(
            text = "Relatórios",
            onClick = { onIr(Relatorios) },
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton(
            text = "Exportar",
            onClick = { onIr(Exportar) },
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
        // 14dp de padding e um Label de 13sp dão ~46dp de alvo — dois a menos
        // que REQ-A11Y-002 exige, e design.md §6.1 já mandava ampliar por aqui.
        // A acessibilidade vence o token: o desenho não muda, o alvo cresce.
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { selected = selecionada },
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
            // Duas linhas, não uma: a 200% "Transações" e "Orçamento" saíam
            // cortados como "Trans" e "Orça" — sem reticências, o que lê como
            // outra palavra em vez de texto truncado.
            //
            // Em duas linhas "Transações" cabe inteira; "Orçamento" ainda não,
            // e aí a reticência é o que importa: a palavra fica visivelmente
            // cortada em vez de virar "Orçament". Quatro abas em 360dp com a
            // fonte dobrada não cabem por extenso, e é o que a própria
            // NavigationBar do Material faz. O leitor de tela continua
            // recebendo o rótulo inteiro, que é o que REQ-A11Y-001 pede.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
        )
    }
}
