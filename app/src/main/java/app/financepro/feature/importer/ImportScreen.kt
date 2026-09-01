package app.financepro.feature.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.component.CategorySticker
import app.financepro.core.ui.component.Chips
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.MoneyText
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.Subheading
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
                .padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Importar extrato", style = Subheading, color = Slush.ink)
            state.recado?.let { Recado(it) }

            when (state.passo) {
                PassoDaImportacao.CONTA -> PassoConta(state, vm::escolherConta)
                PassoDaImportacao.ARQUIVO -> PassoArquivo(state) { arquivo.launch(TIPOS) }
                PassoDaImportacao.MAPEAMENTO -> PassoMapeamento(state, vm)
                PassoDaImportacao.EXTRATO -> PassoExtrato(state, vm::escolherExtrato)
                PassoDaImportacao.PRONTO -> PassoPronto(state, vm::recomecar)
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
        color = Slush.ink,
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
        SlushCard(Modifier.fillMaxWidth()) {
            Text(
                text = linha.mapIndexed { i, c -> "$i: $c" }.joinToString("  ·  "),
                style = Caption,
                color = Slush.ink,
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
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Resumo(state) }
        itemsIndexed(state.linhas) { i, linha ->
            LinhaDaRevisao(
                linha = linha,
                state = state,
                onAlternar = { vm.alternar(i) },
                onCategoria = { vm.categoria(i, it) },
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

@Composable
private fun Resumo(state: ImportState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Conferir antes de gravar", style = Subheading, color = Slush.ink)
        Text(state.nomeDoArquivo, style = Caption, color = Slush.ink)
        // As duplicatas exatas viram um número: a spec manda descartá-las
        // sozinha, e centenas de linhas riscadas atrapalhariam quem precisa
        // conferir as que sobraram.
        if (state.descartadas > 0) {
            Text(descartadasEmPalavras(state.descartadas), style = Caption, color = Slush.ink)
        }
        if (state.possiveis > 0) {
            Text(possiveisEmPalavras(state.possiveis), style = Caption, color = Slush.ink)
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

    SlushCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(DIA.format(candidata.date), style = Caption, color = Slush.ink)
                Text(
                    text = candidata.description.ifBlank { "Sem descrição" },
                    style = BodyStrong,
                    color = Slush.ink,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(cents = candidata.amountCents, style = MoneyCaption)
            }

            // REQ-IMP-009 — a parecida vai **junto**, e não só um aviso: sem a
            // outra linha à vista, "possível duplicata" é injulgável.
            if (linha.avaliada.veredito == Veredito.POSSIVEL_DUPLICATA) {
                linha.avaliada.parecida?.let { parecida ->
                    Text(
                        text = "⚠ Parecida com " + parecida.description.ifBlank { "sem descrição" } +
                            " de " + DIA.format(parecida.date) + ", " + formatBRL(parecida.amountCents),
                        style = Caption,
                        color = Slush.ink,
                    )
                }
            }

            if (linha.incluir) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.categorias.filter { it.kind == esperado }, key = { it.id }) { c ->
                        CategorySticker(
                            category = c,
                            selecionado = c.id == linha.categoriaId,
                            onClick = { onCategoria(c.id) },
                        )
                    }
                }
            }

            GhostButton(
                text = if (linha.incluir) "Incluir · tocar para tirar" else "Fora do lote",
                onClick = onAlternar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PassoPronto(state: ImportState, onRecomecar: () -> Unit) {
    Text("${state.gravadas} transações gravadas.", style = Body, color = Slush.ink)
    GhostButton(
        text = "Importar outro arquivo",
        onClick = onRecomecar,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Recado(texto: String) {
    SlushCard(Modifier.fillMaxWidth()) {
        Text(texto, style = Body, color = Slush.ink, modifier = Modifier.padding(12.dp))
    }
}

/**
 * O singular existe porque "1 parecidas" é o tipo de descuido que faz o usuário
 * desconfiar do resto da tela — e esta é justamente a tela em que ele precisa
 * confiar para confirmar trezentas linhas.
 */
private fun possiveisEmPalavras(quantas: Int): String = if (quantas == 1) {
    "⚠ 1 parecida com uma transação que já existe. Confira antes de confirmar."
} else {
    "⚠ $quantas parecidas com transações que já existem. Confira antes de confirmar."
}

private fun descartadasEmPalavras(quantas: Int): String = if (quantas == 1) {
    "1 já estava na conta e foi descartada."
} else {
    "$quantas já estavam na conta e foram descartadas."
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
