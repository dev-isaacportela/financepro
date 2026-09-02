package app.financepro.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.Rotulo
import app.financepro.core.ui.component.Cartao
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.Tema
import app.financepro.core.ui.theme.Subheading
import java.time.LocalDate

/**
 * Backup e dados. REQ-BAK-001 · REQ-BAK-002 · REQ-BAK-003 · REQ-BAK-004
 *
 * Quatro coisas na mesma tela porque são a mesma pergunta — "o que eu faço com
 * os meus dados?" — e porque a resposta certa quase sempre passa por duas
 * delas: quem vai apagar tudo deveria exportar antes, e quem vai restaurar
 * deveria ter um backup do estado atual.
 *
 * A ordem é a do risco: exportar não estraga nada, backup escreve fora do app,
 * restaurar troca a base, apagar não volta. O que é irreversível fica **no fim**
 * e atrás de uma palavra digitada, nunca a um toque de distância de quem está
 * rolando a tela.
 *
 * `ACTION_CREATE_DOCUMENT` e `ACTION_OPEN_DOCUMENT`, nunca uma pasta do app:
 * quem escolhe o destino e a origem é o seletor do sistema, então o app não pede
 * permissão de armazenamento, não inventa caminho e não deixa cópia do histórico
 * financeiro onde o usuário não sabe que existe.
 */
@Composable
fun ExportScreen(vm: ExportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rolagem = rememberScrollState()

    // O recado sobe a tela junto. Ele mora no topo, e a tela é longa: sem isto,
    // quem toca em "Ler o arquivo" no meio dela recebe "Senha incorreta" a três
    // dedos de rolagem abaixo do polegar — resposta que existe e não é vista é
    // resposta que não existe.
    LaunchedEffect(state.recado) {
        if (state.recado != null) rolagem.animateScrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rolagem)
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Backup e dados", style = Subheading, color = Tema.ink)
        Text(
            text = "Nada sai do aparelho por conta própria: o app não tem acesso " +
                "à internet. Você escolhe cada arquivo e cada destino.",
            style = Body,
            color = Tema.ink,
        )

        // O resultado fica **na tela**, e não num snackbar de 4 segundos: quem
        // acabou de restaurar ou apagar precisa poder reler o que aconteceu.
        state.recado?.let { recado ->
            Cartao(Modifier.fillMaxWidth()) {
                Text(recado, style = Body, color = Tema.ink, modifier = Modifier.padding(12.dp))
            }
        }

        Exportacao(state, vm)
        Backup(state, vm)
        Restauracao(state, vm)
        ZonaDePerigo(state, vm)
    }
}

/** REQ-BAK-001 — os dois formatos em claro, para olhar e para voltar. */
@Composable
private fun Exportacao(state: ExportState, vm: ExportViewModel) {
    val hoje = LocalDate.now()
    val csv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TIPO_CSV),
    ) { destino -> destino?.let(vm::exportarCsv) }

    val json = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TIPO_JSON),
    ) { destino -> destino?.let(vm::exportarJson) }

    Secao("Exportar sem senha")
    GhostButton(
        text = "Transações em CSV",
        onClick = { csv.launch("financepro-transacoes-$hoje.csv") },
        enabled = !state.trabalhando,
        modifier = Modifier.fillMaxWidth(),
    )
    Nota("Abre no Excel e no Google Planilhas, em português.")

    GhostButton(
        text = "Base completa em JSON",
        onClick = { json.launch("financepro-base-$hoje.json") },
        enabled = !state.trabalhando,
        modifier = Modifier.fillMaxWidth(),
    )
    Nota("Legível por qualquer editor — e por qualquer um que abrir o arquivo.")
}

/**
 * REQ-BAK-002 — o backup cifrado, e o aviso que o requisito exige.
 *
 * A senha é pedida **duas vezes** porque o requisito diz, com todas as letras,
 * que perdê-la torna o backup irrecuperável: um erro de digitação num campo só
 * produziria um arquivo que ninguém — nem o dono — consegue abrir, e o defeito
 * só apareceria no dia em que ele fosse necessário.
 */
@Composable
private fun Backup(state: ExportState, vm: ExportViewModel) {
    val arquivo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TIPO_BACKUP),
    ) { destino -> destino?.let(vm::criarBackup) }

    Secao("Backup com senha")
    Nota(
        "AES-256, com a chave derivada da sua senha. Se você perder a senha, o " +
            "backup é irrecuperável: não há como recuperá-la, nem aqui nem em " +
            "lugar nenhum.",
    )

    Senha("Senha", state.senha, vm::senha)
    if (state.senhaCurta) Aviso("Use ao menos $SENHA_MINIMA caracteres.")

    Senha("Repita a senha", state.repetir, vm::repetir)
    if (state.senhasDiferentes) Aviso("As duas senhas estão diferentes.")

    FilledCta(
        text = if (state.trabalhando) "Trabalhando…" else "Criar backup",
        onClick = { arquivo.launch("financepro-backup-${LocalDate.now()}.fpbk") },
        enabled = state.podeCriar,
        modifier = Modifier.fillMaxWidth(),
    )
    Nota("Cifrar e decifrar levam alguns segundos, de propósito: é o que torna " +
        "caro tentar adivinhar a senha no arquivo.")
}

/**
 * REQ-BAK-003 — ler, contar, e só então perguntar.
 *
 * São três passos separados porque a confirmação precisa de números, e os
 * números só existem depois de decifrar. Um "restaurar" de um toque só teria de
 * escolher entre perguntar sem saber o que vem, ou apagar para depois descobrir
 * que a senha estava errada.
 */
@Composable
private fun Restauracao(state: ExportState, vm: ExportViewModel) {
    val escolher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { origem -> origem?.let(vm::escolherOrigem) }

    Secao("Restaurar de um backup")
    GhostButton(
        text = if (state.origem == null) "Escolher arquivo" else "Trocar o arquivo escolhido",
        onClick = { escolher.launch(arrayOf(TIPO_QUALQUER)) },
        enabled = !state.trabalhando,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.origem != null) {
        Senha("Senha do backup", state.senhaDeLeitura, vm::senhaDeLeitura)
        GhostButton(
            text = if (state.trabalhando) "Trabalhando…" else "Ler o arquivo",
            onClick = vm::lerBackup,
            enabled = state.podeLer,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    state.previa?.let { previa ->
        // O número dos dois lados: quantos vêm, e quantos vão embora. "Isto
        // substitui tudo" sem o segundo número é um aviso que não dá para medir.
        Nota(
            "O arquivo traz ${previa.registros} registros. Os " +
                "${state.registrosAtuais} que estão no app agora serão " +
                "substituídos, e isso não volta.",
        )
        FilledCta(
            text = "Confirmar: substituir tudo pelo backup",
            onClick = vm::confirmarRestauracao,
            enabled = !state.trabalhando,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** REQ-BAK-004 — apagar tudo, com a saída de emergência antes da porta. */
@Composable
private fun ZonaDePerigo(state: ExportState, vm: ExportViewModel) {
    Secao("Apagar tudo")
    Nota(
        "Contas, transações, tetos e recorrências, sem volta. Exporte ou faça um " +
            "backup antes — as duas ações estão logo acima nesta tela.",
    )
    OutlinedTextField(
        value = state.confirmacao,
        onValueChange = vm::confirmacao,
        label = { Text("Digite $PALAVRA_DE_APAGAR para liberar") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    GhostButton(
        text = "Apagar todos os dados",
        onClick = vm::apagarTudo,
        enabled = state.podeApagar,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Campo de senha. `PasswordVisualTransformation` e teclado de senha: sem o
 * segundo, o teclado sugere e guarda o que foi digitado no dicionário do
 * aparelho — que é onde uma senha não deve estar.
 */
@Composable
private fun Senha(rotulo: String, valor: String, onValor: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(rotulo) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Secao(titulo: String) {
    Text(titulo, style = Subheading, color = Tema.ink, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Nota(texto: String) = Text(texto, style = Caption, color = Tema.ink)

@Composable
private fun Aviso(texto: String) = Rotulo("⚠ $texto")

private const val TIPO_CSV = "text/csv"
private const val TIPO_JSON = "application/json"

/**
 * O backup não é texto nem tem tipo registrado: `octet-stream` é o que descreve
 * um envelope cifrado, e é o que impede um app de galeria de se oferecer para
 * abri-lo.
 */
private const val TIPO_BACKUP = "application/octet-stream"

/** Para escolher: provedores de nuvem costumam devolver tipo genérico. */
private const val TIPO_QUALQUER = "*/*"
