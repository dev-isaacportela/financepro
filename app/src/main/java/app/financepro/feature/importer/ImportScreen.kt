package app.financepro.feature.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.R
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.component.CategorySticker
import app.financepro.core.ui.component.Chips
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.Icone
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.CanvasDark
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Etiqueta
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Subheading
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Warning
import app.financepro.data.ingest.MapeamentoCsv
import app.financepro.data.ingest.Veredito
import app.financepro.domain.model.CategoryKind
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Importar extrato. REQ-IMP-001 · REQ-IMP-005 · REQ-IMP-010 · Art. 14
 *
 * O fluxo de ingestao.md §1.1, um passo por tela: conta de destino, arquivo,
 * mapeamento (só CSV), revisão, confirmar. Um passo por vez porque cada um
 * depende do anterior — não dá para mapear colunas antes de ler o arquivo, nem
 * revisar antes de saber a conta contra a qual deduplicar.
 *
 * **Nada é gravado antes do "confirmar".** É o Art. 14 e REQ-IMP-010: um app que
 * inventa lançamento perde a confiança na primeira vez que erra, e não tem
 * segunda chance. O botão que grava é o único ponto de escrita do fluxo, e ele
 * fica desabilitado enquanto houver linha incluída sem categoria (REQ-TXN-005).
 *
 * `ACTION_OPEN_DOCUMENT`, nunca varredura de pasta: o app não pede
 * `READ_EXTERNAL_STORAGE` e não sabe onde os arquivos moram (REQ-IMP-001).
 */
@Composable
fun ImportScreen(vm: ImportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val arquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { origem -> origem?.let(vm::arquivoEscolhido) }

    if (state.passo == PassoDaImportacao.REVISAO) {
        Revisao(state, vm)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Importar extrato", style = Subheading, color = Tema.ink)
            state.recado?.let { Recado(it) }

            when (state.passo) {
                PassoDaImportacao.CONTA -> {
                    PassoConta(state, vm::escolherConta)
                    Lotes(state, vm::desfazer)
                }
                PassoDaImportacao.ARQUIVO -> PassoArquivo(state) { arquivo.launch(TIPOS) }
                PassoDaImportacao.MAPEAMENTO -> PassoMapeamento(state, vm)
                PassoDaImportacao.EXTRATO -> PassoExtrato(state, vm::escolherExtrato)
                PassoDaImportacao.PRONTO -> {
                    PassoPronto(state, vm::recomecar)
                    Lotes(state, vm::desfazer)
                }
                PassoDaImportacao.REVISAO -> Unit
            }
        }
    }
}

/** A conta de destino vem **antes** do arquivo: é contra ela que o dedupe compara. */
@Composable
private fun PassoConta(state: ImportState, onConta: (Long) -> Unit) {
    Rotulo("Para qual conta?")
    state.contas.forEach { conta ->
        GhostButton(
            text = conta.name,
            onClick = { onConta(conta.id) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PassoArquivo(state: ImportState, onEscolher: () -> Unit) {
    Rotulo("Extrato de ${state.conta?.name.orEmpty()}")
    Text(
        text = "OFX ou CSV, do jeito que o banco exporta. O arquivo não sai do " +
            "aparelho: o app não tem acesso à internet.",
        style = Body,
        color = Tema.ink,
    )
    FilledCta(
        text = if (state.trabalhando) "Lendo…" else "Escolher arquivo",
        onClick = onEscolher,
        enabled = !state.trabalhando,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** REQ-IMP-002 — o arquivo trouxe mais de uma conta; o app não escolhe sozinho. */
@Composable
private fun PassoExtrato(state: ImportState, onExtrato: (Int) -> Unit) {
    Rotulo("Este arquivo tem mais de uma conta")
    state.extratos.forEachIndexed { i, extrato ->
        GhostButton(
            text = "${extrato.acctId ?: "sem número"} · ${extrato.txns.size} linhas",
            onClick = { onExtrato(i) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * REQ-IMP-005 — quais colunas são o quê, com prévia das primeiras linhas.
 *
 * O palpite do farejador já vem escolhido, e um mapeamento guardado de uma
 * importação anterior ganha dele. O passo continua existindo mesmo assim: o
 * farejador erra em algum banco, e descobrir isso na tela de revisão — com
 * trezentas linhas de data errada — é tarde demais.
 */
@Composable
private fun PassoMapeamento(state: ImportState, vm: ImportViewModel) {
    val mapa = state.mapa ?: MapeamentoCsv(0, 0, 0)
    val colunas = (0 until state.colunas).toList()

    Rotulo("Confira as colunas")
    state.previa.forEach { linha ->
        Cartao(Modifier.fillMaxWidth()) {
            Text(
                text = linha.mapIndexed { i, c -> "$i: $c" }.joinToString("  ·  "),
                style = Caption,
                color = Tema.ink,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    Rotulo("Coluna da data")
    Chips(colunas.map { it to "$it" }, mapa.data) { vm.mapear(mapa.copy(data = it)) }

    Rotulo("Coluna do valor")
    Chips(colunas.map { it to "$it" }, mapa.valor) { vm.mapear(mapa.copy(valor = it)) }

    Rotulo("Coluna da descrição")
    Chips(colunas.map { it to "$it" }, mapa.descricao) { vm.mapear(mapa.copy(descricao = it)) }

    FilledCta(
        text = "Continuar",
        onClick = vm::confirmarMapeamento,
        enabled = !state.trabalhando && mapa.valido(state.colunas),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A revisão obrigatória. REQ-IMP-010 · Art. 14
 *
 * `LazyColumn` e não `Column` rolável: um extrato de ano inteiro traz centenas
 * de linhas, e cada uma carrega um grid de categorias. É o único lugar do app
 * onde a lista é grande o suficiente para a reciclagem pagar.
 */
@Composable
private fun Revisao(state: ImportState, vm: ImportViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Resumo(state) }
        itemsIndexed(state.linhas) { i, linha ->
            LinhaDaRevisao(
                linha = linha,
                state = state,
                onAlternar = { vm.editar(i, linha.copy(incluir = !linha.incluir)) },
                onCategoria = { vm.editar(i, linha.copy(categoriaId = it)) },
            )
        }
        item {
            FilledCta(
                text = confirmarRotulo(state),
                onClick = vm::confirmar,
                enabled = state.podeConfirmar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * O cabeçalho da revisão: onde você está, e o tamanho do que vai gravar.
 *
 * **Os três passos ficam visíveis.** O fluxo sempre teve três — arquivo, colunas,
 * revisão —, e a tela não dizia em qual deles você estava nem quantos faltavam.
 * Numa operação que grava dezenas de linhas de uma vez, "quanto falta" é a
 * pergunta que decide entre continuar e voltar.
 *
 * **E os números viram blocos.** Antes eram frases soltas — "3 duplicatas exatas
 * descartadas" —, todas com o mesmo peso do resto. Linhas, novas e duplicadas
 * são a resposta de "o que vai entrar", e é o que se lê primeiro.
 */
@Composable
private fun Resumo(state: ImportState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.nomeDoArquivo, style = Caption, color = Tema.inkMute)
            Text("Conferir antes de gravar", style = Subheading, color = Tema.ink)
        }

        Passos()

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Numero("Linhas", state.linhas.size + state.descartadas, Modifier.weight(1f))
            Numero("Novas", state.incluidas.size, Modifier.weight(1f), Tema.positivo)
            Numero(
                rotulo = "Duplicadas",
                valor = state.descartadas + state.possiveis,
                modifier = Modifier.weight(1f),
                tinta = if (state.descartadas + state.possiveis > 0) Warning else Tema.ink,
            )
        }
    }
}

/**
 * Os três passos, com o terceiro em curso.
 *
 * Só desenho: quem manda no fluxo é `state.passo`, e esta tela **é** o passo
 * três — chegar aqui já significa que os dois primeiros terminaram. Um estado a
 * mais para dizer o que a própria composição diz seria estado para
 * dessincronizar.
 */
@Composable
private fun Passos() {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassoFeito()
                Trilho()
                PassoFeito()
                Trilho()
                PassoAtual()
            }
            Text(
                text = "Arquivo lido · colunas confirmadas · revisão",
                style = Caption,
                color = Tema.inkMute,
            )
        }
    }
}

@Composable
private fun PassoFeito() = Box(
    modifier = Modifier.size(PASSO).clip(Pill).background(Tema.positivo),
    contentAlignment = Alignment.Center,
) {
    Icone(
        id = R.drawable.ic_confirma,
        descricao = null,
        modifier = Modifier.size(PASSO_GLIFO),
        tint = CanvasDark,
    )
}

@Composable
private fun PassoAtual() = Box(
    modifier = Modifier.size(PASSO).clip(Pill).background(Tema.ink),
    contentAlignment = Alignment.Center,
) {
    Text("3", style = Caption, color = Tema.paper)
}

@Composable
private fun RowScope.Trilho() = Box(
    Modifier.weight(1f).height(TRILHO).clip(Pill).background(Tema.hairline),
)

private val PASSO = 22.dp
private val PASSO_GLIFO = 12.dp
private val TRILHO = 1.dp

/**
 * Um número do lote, em bloco.
 *
 * [tinta] só existe para "Novas" e "Duplicadas": o primeiro é o que vai entrar e
 * o segundo é o que pede atenção. "Linhas" fica em `ink` porque é só o tamanho
 * do arquivo, e três números coloridos não teriam hierarquia nenhuma.
 */
@Composable
private fun Numero(
    rotulo: String,
    valor: Int,
    modifier: Modifier = Modifier,
    tinta: androidx.compose.ui.graphics.Color? = null,
) {
    Cartao(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(rotulo.uppercase(), style = Etiqueta, color = Tema.inkMute)
            Text("$valor", style = Subheading, color = tinta ?: Tema.ink)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinhaDaRevisao(
    linha: LinhaEmRevisao,
    state: ImportState,
    onAlternar: () -> Unit,
    onCategoria: (Long) -> Unit,
) {
    val candidata = linha.avaliada.candidata
    val esperado =
        if (candidata.amountCents >= 0) CategoryKind.INCOME else CategoryKind.EXPENSE

    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // A caixa de marcar substitui o botão de largura cheia que ficava
                // no rodapé da linha: incluir ou tirar é o gesto mais repetido
                // desta tela, e ele estava a uma leitura de distância do item.
                CaixaDeMarcar(marcada = linha.incluir, onClick = onAlternar)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = candidata.description.ifBlank { "Sem descrição" },
                        style = BodyStrong,
                        color = Tema.ink,
                    )
                    Text(
                        text = DIA.format(candidata.date) + " · " + situacao(linha, state),
                        style = Caption,
                        color = Tema.inkMute,
                    )
                }
                MoneyText(cents = candidata.amountCents, style = MoneyCaption, porSinal = true)
            }

            // REQ-IMP-009 — a parecida vai **junto**, e não só um aviso: sem a
            // outra linha à vista, "possível duplicata" é injulgável.
            if (linha.avaliada.veredito == Veredito.POSSIVEL_DUPLICATA) {
                linha.avaliada.parecida?.let { parecida ->
                    Aviso(
                        texto = "Parecida com " + parecida.description.ifBlank { "sem descrição" } +
                            " de " + DIA.format(parecida.date) + ", " + formatBRL(parecida.amountCents),
                    )
                }
            }

            // **O seletor só aparece em quem precisa dele.** Antes vinha em toda
            // linha incluída, e num extrato de 25 linhas isso é uma cartela de
            // categorias por transação — a tela ficava com metros de rolagem
            // para resolver as duas ou três que faltam.
            //
            // Quem já tem categoria mostra qual, em texto, e trocá-la depois é
            // editar a transação (T-050) — o mesmo caminho de qualquer outra
            // correção, e não um segundo lugar que faz a mesma coisa.
            if (linha.incluir && linha.categoriaId == null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.categorias.filter { it.kind == esperado }, key = { it.id }) { c ->
                        CategorySticker(
                            category = c,
                            // Nunca marcado, e não por esquecimento: escolher preenche
                            // `categoriaId`, o `if` acima vira falso e a fileira sai de
                            // cena. Chip visível e chip não escolhido são a mesma coisa.
                            selecionado = false,
                            onClick = { onCategoria(c.id) },
                        )
                    }
                }
            }

        }
    }
}

/**
 * A caixa de marcar da revisão. REQ-A11Y-002 · REQ-A11Y-003
 *
 * Marcada é **preenchimento com visto**; desmarcada é o contorno vazio. Duas
 * formas diferentes, e não duas cores — quem não distingue as tintas ainda vê o
 * visto aparecer.
 *
 * `Role.Checkbox` e `toggleable` no lugar de `clickable`: é o que faz o leitor de
 * tela anunciar "caixa de seleção, marcada" e oferecer o gesto certo. Com um
 * `clickable` genérico ele diria só "botão", e a linha ficaria sem estado.
 */
@Composable
private fun CaixaDeMarcar(marcada: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(CAIXA)
            .clip(Pill)
            .background(if (marcada) Tema.ink else Tema.paper)
            .border(OutlineWidth, if (marcada) Tema.ink else Tema.inkMute, Pill)
            .toggleable(
                value = marcada,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            Icone(
                id = R.drawable.ic_confirma,
                descricao = null,
                modifier = Modifier.size(CAIXA_GLIFO),
                tint = Tema.paper,
            )
        }
    }
}

private val CAIXA = 24.dp
private val CAIXA_GLIFO = 13.dp

/**
 * O aviso de possível duplicata, em faixa.
 *
 * Era uma linha de `Caption` começando com "⚠", do mesmo tamanho e da mesma
 * tinta que a descrição logo acima — lia como continuação da transação, não como
 * alerta. A barra à esquerda separa sem usar preenchimento saturado, que
 * REQ-DS-006 não autoriza com texto por cima.
 *
 * Laranja passa em 6.40:1 sobre o card escuro; no claro dá 2.53:1 e serve só à
 * barra, então o texto fica em `ink` (REQ-DS-007).
 */
@Composable
private fun Aviso(texto: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(BARRA_AVISO).fillMaxHeight().clip(Pill).background(Warning))
        Text(texto, style = Caption, color = Tema.ink)
    }
}

private val BARRA_AVISO = 3.dp

/** O que a linha diz de si abaixo da data: a sugestão, ou o que falta. */
@Composable
private fun situacao(linha: LinhaEmRevisao, state: ImportState): String = when {
    !linha.incluir -> "fora do lote"
    linha.categoriaId == null -> "sem categoria"
    else -> "sugerido: " + state.categorias.firstOrNull { it.id == linha.categoriaId }?.name.orEmpty()
}

@Composable
private fun PassoPronto(state: ImportState, onRecomecar: () -> Unit) {
    Text("${state.gravadas} transações gravadas.", style = Body, color = Tema.ink)
    GhostButton(
        text = "Importar outro arquivo",
        onClick = onRecomecar,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Os lotes já importados, com o desfazer. REQ-IMP-011 · ingestao.md §3.1
 *
 * Aparece nos dois momentos em que ele é útil: antes de importar, para conferir
 * o que já entrou, e logo depois de importar, que é quando se descobre que o
 * arquivo era o errado. Uma tela própria escondida no menu seria um caminho a
 * mais justamente para quem está com pressa de desfazer.
 */
@Composable
private fun Lotes(state: ImportState, onDesfazer: (Long) -> Unit) {
    if (state.lotes.isEmpty()) return

    Rotulo("Importações anteriores")
    state.lotes.forEach { lote ->
        Cartao(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = lote.sourceName.ifBlank { lote.sourceType },
                    style = BodyStrong,
                    color = Tema.ink,
                )
                Text(
                    text = QUANDO.format(java.time.Instant.ofEpochMilli(lote.importedAt)) +
                        " · " + lote.txnCount + " linhas",
                    style = Caption,
                    color = Tema.ink,
                )
                GhostButton(
                    text = "Desfazer este lote",
                    onClick = { onDesfazer(lote.id) },
                    enabled = !state.trabalhando,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Recado(texto: String) {
    Cartao(Modifier.fillMaxWidth()) {
        Text(texto, style = Body, color = Tema.ink, modifier = Modifier.padding(12.dp))
    }
}

/**
 * O rótulo diz o que falta, e não só "salvar".
 *
 * REQ-TXN-005 exige categoria em receita e despesa, então o lote não pode ir com
 * linha sem ela. Um botão desabilitado e mudo faria o usuário procurar o que
 * está errado em trezentas linhas.
 */
private fun confirmarRotulo(state: ImportState): String = when {
    state.trabalhando -> "Gravando…"
    state.incluidas.isEmpty() -> "Nada selecionado"
    state.semCategoria == 1 -> "Falta 1 categoria"
    state.semCategoria > 1 -> "Faltam ${state.semCategoria} categorias"
    else -> "Gravar ${state.incluidas.size} transações"
}

/**
 * Os tipos que ingestao.md §1.1 lista. `text/plain` e `octet-stream` entram
 * porque OFX exportado com extensão errada é comum, e provedor de nuvem devolve
 * tipo genérico para quase tudo.
 */
private val TIPOS = arrayOf(
    "application/x-ofx",
    "application/ofx",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream",
)

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", PT_BR)

/** O carimbo do lote é milissegundo de relógio; a tela mostra o dia e a hora. */
private val QUANDO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR)
        .withZone(java.time.ZoneId.systemDefault())
