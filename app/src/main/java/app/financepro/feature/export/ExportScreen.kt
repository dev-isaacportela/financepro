package app.financepro.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.Subheading
import java.time.LocalDate

/**
 * Exportar. REQ-BAK-001
 *
 * Dois formatos porque são duas perguntas: o CSV é para **olhar** — abre na
 * planilha, filtra, soma —, e o JSON é para **voltar**, com as colunas que o
 * domínio não carrega e que a restauração da T-035 precisa.
 *
 * `ACTION_CREATE_DOCUMENT` e não uma pasta do app: quem escolhe o destino é o
 * seletor do sistema, então o app não pede permissão de armazenamento, não
 * inventa um caminho e não deixa cópia do histórico financeiro num lugar que o
 * usuário não sabe que existe.
 */
@Composable
fun ExportScreen(vm: ExportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hoje = LocalDate.now()

    val csv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TIPO_CSV),
    ) { destino -> destino?.let(vm::exportarCsv) }

    val json = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TIPO_JSON),
    ) { destino -> destino?.let(vm::exportarJson) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Exportar", style = Subheading, color = Slush.ink)

        Text(
            text = "O arquivo vai para onde você escolher. Nada sai do aparelho por " +
                "conta própria: o app não tem acesso à internet.",
            style = Body,
            color = Slush.ink,
        )

        GhostButton(
            text = "Transações em CSV",
            onClick = { csv.launch("financepro-transacoes-$hoje.csv") },
            enabled = !state.trabalhando,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Abre no Excel e no Google Planilhas, em português.",
            style = Caption,
            color = Slush.ink,
        )

        GhostButton(
            text = "Base completa em JSON",
            onClick = { json.launch("financepro-base-$hoje.json") },
            enabled = !state.trabalhando,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Contas, categorias, transações, tetos e recorrências. É o " +
                "arquivo que serve para restaurar.",
            style = Caption,
            color = Slush.ink,
        )

        // O resultado fica **na tela**, e não num snackbar de 4 segundos: quem
        // exporta o histórico financeiro quer poder conferir que deu certo, e a
        // frase some junto com a próxima tentativa, não com o tempo.
        state.recado?.let { recado ->
            SlushCard(Modifier.fillMaxWidth()) {
                Text(recado, style = Body, color = Slush.ink, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

private const val TIPO_CSV = "text/csv"
private const val TIPO_JSON = "application/json"
