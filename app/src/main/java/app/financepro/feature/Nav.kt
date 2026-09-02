package app.financepro.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import app.financepro.R
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.component.Fab
import app.financepro.core.ui.component.Icone
import app.financepro.core.ui.component.Superficie
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.NavRotulo
import app.financepro.core.ui.theme.NavRotuloForte
import app.financepro.core.ui.theme.Label
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Subheading
import app.financepro.data.prefs.SecurityPrefs
import app.financepro.data.repo.AccountRepository
import app.financepro.data.repo.CategoryRepository
import app.financepro.data.repo.RecurringRepository
import app.financepro.feature.accounts.AccountsScreen
import app.financepro.feature.budget.BudgetScreen
import app.financepro.feature.card.CardScreen
import app.financepro.feature.categories.CategoriesScreen
import app.financepro.feature.export.ExportScreen
import app.financepro.feature.home.HomeScreen
import app.financepro.feature.importer.ImportScreen
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.YearMonth
import kotlin.math.abs
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
 * Tema. Seleção é sinalizada por **preenchimento**, não só por cor (REQ-A11Y-003).
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

@Serializable
data object Importar

/**
 * A fatura de um cartão. O único destino com argumento — e mesmo aqui o id vai
 * no objeto, não numa string interpolada: o `CardViewModel` o lê de volta com
 * `toRoute`, e um erro de nome vira erro de compilação em vez de tela em branco.
 */
@Serializable
data class Cartao(val id: Long)

/** Tempo da entrada e da saída da pílula quando nenhuma aba está selecionada. */
private const val DURACAO_ABA = 180

/** Vão entre abas. Entra na conta da pílula que desliza, por isso é constante. */
private val ESPACO_ABA = 4.dp

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
        containerColor = Tema.paper,
        bottomBar = { BarraInferior(nav, onNovoLancamento = { lancando = true }) },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = Inicio,
            modifier = Modifier.fillMaxSize().padding(insets),
            // O movimento das telas mora em Movimento.kt. As quatro lambdas
            // apontam para as mesmas duas funções de propósito: trocar de aba é
            // um pop por dentro (`popUpTo(Inicio)`), então "entrou" e "voltou"
            // não dizem nada sobre para que lado o dedo foi — quem diz é a
            // ordem das abas, e ela vale igual nos quatro casos.
            enterTransition = { entradaSlush() },
            exitTransition = { saidaSlush() },
            popEnterTransition = { entradaSlush() },
            popExitTransition = { saidaSlush() },
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
    composable<Importar> { ImportScreen() }
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
/**
 * As contagens dos blocos do hub. REQ-UI-001
 *
 * Existe para o "Mais" poder dizer o **estado** de cada destino sem que ninguém
 * entre nele: "3 ativas · 1 arquivada" responde a pergunta que faria alguém
 * abrir a tela de contas só para conferir.
 *
 * Fica aqui, e não no `RaizViewModel`, porque aquele responde por bloqueio e
 * onboarding — coisas que existem antes de qualquer tela. Este só é observado
 * enquanto o hub está na frente.
 */
@HiltViewModel
class MaisViewModel @Inject constructor(
    contas: AccountRepository,
    categorias: CategoryRepository,
    recorrencias: RecurringRepository,
) : ViewModel() {
    val state = combine(
        contas.observeAll(),
        categorias.observeActive(),
        recorrencias.observeAll(),
    ) { cs, cats, rs ->
        MaisState(
            contasAtivas = cs.count { !it.archived },
            contasArquivadas = cs.count { it.archived },
            categorias = cats.size,
            recorrencias = rs.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MaisState())

    private companion object {
        const val PARADA_MS = 5_000L
    }
}

data class MaisState(
    val contasAtivas: Int = 0,
    val contasArquivadas: Int = 0,
    val categorias: Int = 0,
    val recorrencias: Int = 0,
)

/**
 * O "Mais" da barra: um índice, não uma tela.
 *
 * Contas, Categorias, Recorrências e Relatórios são destinos próprios em vez de
 * abas porque a barra tem quatro lugares (REQ-UI-001) e nenhum deles deve ser
 * gasto com algo que se mexe uma vez por mês. O que a recorrência produz
 * aparece onde importa: o bloco "Próximas contas" do dashboard.
 *
 * **Três grupos nomeados, não sete botões iguais.** Sete pílulas de largura
 * cheia empilhadas não têm hierarquia nenhuma: quem procura "Importar" lê as
 * sete. O nome do grupo já responde "onde fica isso", e a grade de dois deixa a
 * tela caber sem rolagem.
 *
 * Cada bloco carrega a própria contagem. É o que transforma o índice em
 * resposta — dá para decidir se vale entrar sem entrar.
 *
 * Relatórios sai da grade e vira linha larga: é consulta, não cadastro, e é o
 * item que se abre com mais frequência.
 */
@Composable
private fun MaisScreen(
    onIr: (Any) -> Unit,
    bloqueio: Boolean,
    onAlternarBloqueio: () -> Unit,
    vm: MaisViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Mais", style = Subheading, color = Tema.ink)

        Grupo("Cadastro") {
            LinhaDeBlocos {
                Bloco(
                    titulo = "Contas",
                    detalhe = contasDetalhe(state),
                    onClick = { onIr(Contas) },
                    modifier = Modifier.weight(1f),
                )
                Bloco(
                    titulo = "Categorias",
                    detalhe = plural(state.categorias, "categoria", "categorias"),
                    onClick = { onIr(Categorias) },
                    modifier = Modifier.weight(1f),
                )
            }
            LinhaDeBlocos {
                Bloco(
                    titulo = "Recorrências",
                    detalhe = plural(state.recorrencias, "regra", "regras"),
                    onClick = { onIr(Recorrencias) },
                    modifier = Modifier.weight(1f),
                )
                // Uma preferência não é uma tela de ajustes. O estado vai
                // **escrito** no detalhe, não sinalizado por cor (REQ-A11Y-003),
                // e é o mesmo bloco — que também é o que o leitor anuncia.
                Bloco(
                    titulo = "Bloqueio do app",
                    detalhe = if (bloqueio) "Ligado" else "Desligado",
                    onClick = onAlternarBloqueio,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Grupo("Dados") {
            LinhaDeBlocos {
                Bloco(
                    titulo = "Importar",
                    detalhe = "OFX ou CSV do banco",
                    onClick = { onIr(Importar) },
                    modifier = Modifier.weight(1f),
                )
                Bloco(
                    titulo = "Backup",
                    detalhe = "Cifrado, e exportação CSV",
                    onClick = { onIr(Exportar) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Grupo("Análise") {
            Cartao(Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir") { onIr(Relatorios) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Relatórios", style = BodyStrong, color = Tema.ink)
                        Text(
                            text = "Gastos por categoria, evolução do mês",
                            style = Caption,
                            color = Tema.inkMute,
                        )
                    }
                    Text("›", style = Subheading, color = Tema.inkMute)
                }
            }
        }
    }
}

/** Um grupo do hub: o rótulo em caixa alta e o que vem embaixo dele. */
@Composable
private fun Grupo(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo.uppercase(), style = Caption, color = Tema.inkMute)
        conteudo()
    }
}

/** Dois blocos lado a lado, de altura igual — senão a grade fica dentada. */
@Composable
private fun LinhaDeBlocos(conteudo: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(IntrinsicSize.Min),
        content = conteudo,
    )
}

/**
 * Um destino do hub.
 *
 * [detalhe] não é legenda: é o estado que dispensa a visita. Por isso ele nunca
 * é opcional — um bloco sem nada a dizer sobre si já é sinal de que o destino
 * não merecia um bloco.
 */
@Composable
private fun Bloco(titulo: String, detalhe: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Cartao(
        modifier
            .fillMaxHeight()
            .clickable(onClickLabel = "Abrir", onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(titulo, style = BodyStrong, color = Tema.ink)
            Text(detalhe, style = Caption, color = Tema.inkMute)
        }
    }
}

/** "1 arquivada" e "2 arquivadas" — o plural sai daqui, não de um `+ "s"`. */
private fun plural(n: Int, singular: String, plural: String) =
    if (n == 1) "1 $singular" else "$n $plural"

private fun contasDetalhe(state: MaisState): String {
    val ativas = plural(state.contasAtivas, "ativa", "ativas")
    return if (state.contasArquivadas == 0) {
        ativas
    } else {
        ativas + " · " + plural(state.contasArquivadas, "arquivada", "arquivadas")
    }
}

/**
 * A barra de abas. REQ-UI-001 · REQ-A11Y-002 · REQ-A11Y-003 ·
 * [design.md](../../../../../../../../docs/design.md) §6.1
 *
 * Quatro abas com **ícone e rótulo**, e o botão de novo lançamento no fim da
 * mesma barra. É o desenho do protótipo, e ele resolve uma coisa que a versão
 * anterior não resolvia: com quatro pílulas contornadas lado a lado, a barra
 * tinha mais traço branco que conteúdo, e o botão flutuante cobria a última
 * linha da lista.
 *
 * **Seleção não é só cor** (REQ-A11Y-003). São três canais ao mesmo tempo: a
 * tinta sobe de `inkMute` para `ink`, o rótulo engorda de 400 para 600, e a
 * semântica marca `selected` — que é o que o leitor de tela anuncia, e o único
 * que funciona para quem não distingue os dois cinzas.
 *
 * A pílula que deslizava saiu com o sistema visual anterior. Ela era bonita e
 * era a gramática de lá: seleção por preenchimento, sobre papel branco. Aqui a
 * ação preenchida é o botão de lançar, e uma segunda superfície preenchida na
 * mesma barra disputaria com ele.
 */
@Composable
private fun BarraInferior(nav: NavHostController, onNovoLancamento: () -> Unit) {
    val atual by nav.currentBackStackEntryAsState()
    val destino = atual?.destination
    val indice = ABAS.indexOfFirst { (rota, _) -> destino?.hasRoute(rota::class) == true }

    Superficie(
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ABAS.forEachIndexed { i, (destino, rotulo) ->
                Aba(
                    icone = ICONES_DAS_ABAS[i],
                    rotulo = rotulo,
                    selecionada = i == indice,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // `launchSingleTop` e o popUpTo na raiz mantêm a pilha em
                        // uma tela por aba: sem eles, ir e voltar entre abas
                        // empilha telas que o botão de voltar teria de desfazer
                        // uma a uma.
                        nav.navigate(destino) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            // O botão de lançar mora **dentro** da barra, e não flutuando sobre a
            // lista. Flutuando ele cobria a última linha de toda tela rolável —
            // dava para rolar além, mas o app parecia ter um dedo em cima do
            // conteúdo o tempo todo.
            Fab(
                onClick = onNovoLancamento,
                rotulo = "Novo lançamento",
                modifier = Modifier.size(BOTAO_NOVO),
            ) {
                Icone(id = R.drawable.ic_novo, descricao = null, modifier = Modifier.size(GLIFO_NOVO))
            }
        }
    }
}

/** Na ordem de [ABAS]. Duas listas seriam duas coisas para desalinhar. */
private val ICONES_DAS_ABAS = listOf(
    R.drawable.ic_inicio,
    R.drawable.ic_transacoes,
    R.drawable.ic_orcamento,
    R.drawable.ic_mais,
)

private val BOTAO_NOVO = 48.dp
private val GLIFO_NOVO = 20.dp

/**
 * Uma aba: ícone em cima, rótulo embaixo.
 *
 * O rótulo fica em `Caption` e não em `Label` porque quatro palavras em 360dp
 * precisam caber sem reticências — "Transações" é a mais longa, e é a que manda
 * no tamanho.
 *
 * `clearAndSetSemantics` junta ícone e rótulo numa coisa só: sem ele o leitor
 * anuncia o desenho e o texto em sequência, e "Transações Transações" é o que
 * sai.
 */
@Composable
private fun Aba(
    @DrawableRes icone: Int,
    rotulo: String,
    selecionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tinta = if (selecionada) Tema.ink else Tema.inkMute

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(Pill)
            .clickable(onClickLabel = rotulo, onClick = onClick)
            .padding(vertical = 6.dp)
            .clearAndSetSemantics {
                contentDescription = rotulo
                selected = selecionada
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icone(id = icone, descricao = null, modifier = Modifier.size(GLIFO_ABA), tint = tinta)
        Text(
            text = rotulo,
            style = if (selecionada) NavRotuloForte else NavRotulo,
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val GLIFO_ABA = 20.dp
