package app.financepro.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.CategorySticker
import app.financepro.core.ui.component.Chips
import app.financepro.core.ui.component.EstadoVazio
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyField
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.LightGreen
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Formas
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.Frequency
import app.financepro.domain.usecase.RecurrenceSpec
import app.financepro.domain.usecase.RecurringRule
import app.financepro.domain.usecase.ValidationError
import app.financepro.domain.usecase.nextOccurrence
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lançamentos recorrentes. REQ-REC-001 · REQ-REC-002 · REQ-REC-005
 *
 * A lista existe para responder duas perguntas — "o que se repete?" e "quando
 * cai de novo?" — e a segunda é a que justifica a tela: a regra em si já vive
 * no banco desde a T-031, mas ninguém tinha onde criá-la nem como saber quando
 * ela cobra.
 *
 * Fica no "Mais", não numa aba: cadastrar recorrência é coisa de uma vez por
 * mês, e a barra tem quatro lugares (REQ-UI-001) para o que se usa todo dia. O
 * resultado dela aparece onde importa — o bloco "Próximas contas" do dashboard.
 */
@Composable
fun RecurringScreen(vm: RecurringViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Recorrências", style = Subheading, color = Tema.ink)

        if (state.regras.isEmpty() && state.carregado) {
            // REQ-UI-006 — o vazio traz a ação que o preenche, e diz para quê.
            EstadoVazio(
                titulo = "NADA SE REPETE",
                sticker = LightGreen,
                descricao = "Cadastre o aluguel, o salário ou a assinatura, e eles " +
                    "aparecem sozinhos em Próximas contas.",
            )
        } else {
            state.regras.forEach { regra ->
                Linha(regra = regra, state = state, onEditar = { vm.abrir(regra) })
            }
        }

        FilledCta(
            text = if (state.regras.isEmpty()) "Criar recorrência" else "Nova recorrência",
            onClick = { vm.abrir(null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.folha != null) FolhaDeRegra(state, vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Linha(regra: RecurringRule, state: RecurringState, onEditar: () -> Unit) {
    val categoria = state.categoriaDe(regra.categoryId)
    val titulo = regra.description.ifBlank { categoria?.name ?: rotuloDoTipo(regra.type) }

    Cartao(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(12.dp)
                // A linha inteira vira uma frase só para o leitor de tela
                // (REQ-A11Y-001); o botão dentro dela continua alcançável,
                // porque componente clicável traz semântica própria.
                .semantics(mergeDescendants = true) {
                    contentDescription = titulo + ", " + quando(regra, state.hoje)
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = titulo,
                    style = BodyStrong,
                    color = Tema.ink,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(cents = regra.amountCents, style = MoneyCaption)
            }
            Text(quando(regra, state.hoje), style = Caption, color = Tema.ink)
            GhostButton(text = "Editar", onClick = onEditar)
        }
    }
}

/**
 * A folha de cadastro. REQ-REC-001 · REQ-REC-002 · REQ-REC-005
 *
 * A mesma ordem de campos da folha de lançamento — valor, tipo, conta,
 * categoria — e só depois o que é exclusivo da recorrência. Quem já lançou uma
 * despesa reconhece a metade de cima, e a de baixo é a única coisa nova.
 *
 * Dividida em três porque são três perguntas diferentes: **o que** se lança,
 * **quando** se repete, e **como** entra. Numa função só o formulário passava
 * de 140 linhas, e mexer no espaçamento de um campo obrigava a rolar os outros
 * doze.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDeRegra(state: RecurringState, vm: RecurringViewModel) {
    val folha = state.folha ?: return
    var seletor by remember { mutableStateOf<CampoDeData?>(null) }

    ModalBottomSheet(
        onDismissRequest = vm::fechar,
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
            Text(
                text = if (folha.editando) "Editar recorrência" else "Nova recorrência",
                style = Subheading,
                color = Tema.ink,
            )
            OQueSeLanca(state, folha, vm)
            QuandoSeRepete(state, folha, vm, onEscolherData = { seletor = it })
            ComoEntra(folha, vm)
        }
    }

    if (seletor != null) {
        SeletorDeData(
            inicial = if (seletor == CampoDeData.FIM) folha.fim ?: folha.inicio else folha.inicio,
            onEscolher = { escolhida ->
                if (seletor == CampoDeData.FIM) {
                    vm.editar(folha.copy(fim = escolhida))
                } else {
                    vm.editar(folha.copy(inicio = escolhida))
                }
            },
            onDismiss = { seletor = null },
        )
    }
}

/** Valor, tipo, descrição, conta e categoria — os campos que a transação teria. */
@Composable
private fun OQueSeLanca(state: RecurringState, folha: RegraEmEdicao, vm: RecurringViewModel) {
    MoneyField(cents = folha.cents, onCentsChange = { vm.editar(folha.copy(cents = it)) })
    Erro(state.erroDe(ValidationError.Campo.VALOR))

    Chips(
        itens = TxnType.entries.map { it to rotuloDoTipo(it) },
        selecionado = folha.tipo,
        onClick = vm::tipo,
    )

    // O único campo de texto livre do app até aqui: no lançamento de três
    // toques a descrição sai da categoria, mas "Aluguel" e "Netflix" numa lista
    // de regras precisam se distinguir sem abrir cada uma.
    OutlinedTextField(
        value = folha.descricao,
        onValueChange = { vm.editar(folha.copy(descricao = it)) },
        label = { Text("Descrição") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Rotulo("Conta")
    Chips(
        itens = state.contas.map { it.id to it.name },
        selecionado = folha.contaId,
        onClick = { vm.editar(folha.copy(contaId = it)) },
    )
    Erro(state.erroDe(ValidationError.Campo.CONTA))

    if (folha.mostraDestino) {
        Rotulo("Para")
        Chips(
            // A origem não pode ser destino de si mesma (REQ-TXN-004).
            itens = state.contas.filter { it.id != folha.contaId }.map { it.id to it.name },
            selecionado = folha.destinoId,
            onClick = { vm.editar(folha.copy(destinoId = it)) },
        )
        Erro(state.erroDe(ValidationError.Campo.CONTA_DESTINO))
    }

    if (folha.mostraCategoria) {
        Rotulo("Categoria")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.categorias, key = { it.id }) { categoria ->
                CategorySticker(
                    category = categoria,
                    selecionado = categoria.id == folha.categoriaId,
                    onClick = { vm.editar(folha.copy(categoriaId = categoria.id)) },
                )
            }
        }
        Erro(state.erroDe(ValidationError.Campo.CATEGORIA))
    }
}

/** Frequência, intervalo e as duas datas. REQ-REC-001 · REQ-REC-002 */
@Composable
private fun QuandoSeRepete(
    state: RecurringState,
    folha: RegraEmEdicao,
    vm: RecurringViewModel,
    onEscolherData: (CampoDeData) -> Unit,
) {
    Rotulo("Repete")
    Chips(
        itens = Frequency.entries.map { it to rotuloDaFrequencia(it) },
        selecionado = folha.frequencia,
        onClick = { vm.editar(folha.copy(frequencia = it)) },
    )

    // REQ-REC-002 — "a cada N". Doze cobre de toda semana a de ano em ano;
    // passar disso é regra que se escreve melhor mudando a frequência, e a
    // fileira é um LazyRow, então doze chips custam o mesmo que três.
    Rotulo("A cada")
    Chips(
        itens = INTERVALOS.map { it to it.toString() },
        selecionado = folha.intervalo,
        onClick = { vm.editar(folha.copy(intervalo = it)) },
    )

    Rotulo("Começa em")
    GhostButton(
        text = DIA.format(folha.inicio),
        onClick = { onEscolherData(CampoDeData.INICIO) },
        modifier = Modifier.fillMaxWidth(),
    )
    Erro(state.erroDe(ValidationError.Campo.DATA))

    Rotulo("Termina em")
    GhostButton(
        text = folha.fim?.let { DIA.format(it) } ?: "Sem data de fim",
        onClick = { onEscolherData(CampoDeData.FIM) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (folha.fim != null) {
        GhostButton(
            text = "Tirar a data de fim",
            onClick = { vm.editar(folha.copy(fim = null)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Como a ocorrência entra, e o que fazer com a regra. REQ-REC-005
 *
 * Os interruptores levam o estado **escrito** no rótulo, e o mesmo botão o
 * alterna: cor não é sinal único (REQ-A11Y-003), e um `Switch` ao lado de um
 * texto seriam duas coisas para o leitor de tela anunciar em vez de uma.
 */
@Composable
private fun ComoEntra(folha: RegraEmEdicao, vm: RecurringViewModel) {
    GhostButton(
        text = if (folha.autoPost) {
            "Lança sozinho, já efetivado"
        } else {
            "Entra como prevista, para eu confirmar"
        },
        onClick = { vm.editar(folha.copy(autoPost = !folha.autoPost)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (folha.editando) {
        // Pausar existe para não empurrar quem quer parar de gerar até o botão
        // de excluir: a regra sai da geração e o histórico fica inteiro.
        GhostButton(
            text = if (folha.ativa) "Ativa · tocar para pausar" else "Pausada · tocar para ativar",
            onClick = { vm.editar(folha.copy(ativa = !folha.ativa)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    FilledCta(text = "Salvar", onClick = vm::salvar, modifier = Modifier.fillMaxWidth())

    if (folha.editando) {
        // O rótulo diz o que acontece com o passado: um "Excluir" seco deixaria
        // a dúvida de se o aluguel pago em março vai sumir do extrato.
        GhostButton(
            text = "Excluir a regra, mantendo os lançamentos",
            onClick = vm::excluir,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Qual das duas datas o calendário está editando. */
private enum class CampoDeData { INICIO, FIM }

/**
 * O calendário do Material, e não um seletor próprio: ele já vem no
 * `material3` que o app usa, já herda papel e tinta do `colorScheme` (Theme.kt)
 * e já traz teclado, leitor de tela e localização. Desenhar um por fora seria
 * reescrever tudo isso para caber na mesma gramática que ele já obedece.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorDeData(
    inicial: LocalDate,
    onEscolher: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.toEpochDay() * MILIS_POR_DIA,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp,
        confirmButton = {
            GhostButton(
                text = "Escolher",
                onClick = {
                    // O seletor devolve meia-noite em UTC, que é exatamente o
                    // epochDay multiplicado — a volta é a divisão, sem fuso no
                    // meio. Converter por `Instant` local deslocaria a data em
                    // um dia para quem está a oeste de Greenwich, que é o Brasil
                    // inteiro.
                    estado.selectedDateMillis?.let { onEscolher(LocalDate.ofEpochDay(it / MILIS_POR_DIA)) }
                    onDismiss()
                },
            )
        },
        dismissButton = { GhostButton(text = "Cancelar", onClick = onDismiss) },
    ) {
        DatePicker(state = estado)
    }
}

@Composable
private fun Erro(mensagem: String?) {
    if (mensagem != null) Text("⚠ $mensagem", style = Caption, color = Tema.ink)
}

/**
 * A frase que a lista mostra: com que frequência, e quando cai de novo.
 *
 * Regra pausada não diz data nenhuma — ela não vai gerar, e anunciar "Próxima
 * 10/10" para algo que não vai acontecer é pior que não dizer nada.
 */
private fun quando(regra: RecurringRule, hoje: LocalDate): String {
    val repeticao = repeticao(regra.spec)
    val proxima = regra.nextOccurrence(hoje)
    return when {
        !regra.active -> "Pausada · " + repeticao
        proxima == null -> "Encerrada · " + repeticao
        else -> repeticao + " · próxima " + DIA.format(proxima)
    }
}

private fun repeticao(spec: RecurrenceSpec): String = when (spec.frequency) {
    Frequency.DAILY -> if (spec.interval == 1) "Todo dia" else "A cada ${spec.interval} dias"
    Frequency.WEEKLY -> if (spec.interval == 1) "Toda semana" else "A cada ${spec.interval} semanas"
    Frequency.MONTHLY -> if (spec.interval == 1) "Todo mês" else "A cada ${spec.interval} meses"
    Frequency.YEARLY -> if (spec.interval == 1) "Todo ano" else "A cada ${spec.interval} anos"
}

private fun rotuloDoTipo(tipo: TxnType) = when (tipo) {
    TxnType.EXPENSE -> "Despesa"
    TxnType.INCOME -> "Receita"
    TxnType.TRANSFER -> "Transferência"
}

private fun rotuloDaFrequencia(f: Frequency) = when (f) {
    Frequency.DAILY -> "Dia"
    Frequency.WEEKLY -> "Semana"
    Frequency.MONTHLY -> "Mês"
    Frequency.YEARLY -> "Ano"
}

private val INTERVALOS = (1..12).toList()

private const val MILIS_POR_DIA = 86_400_000L

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR)
