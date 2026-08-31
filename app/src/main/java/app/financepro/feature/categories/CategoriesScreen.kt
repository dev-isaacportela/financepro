package app.financepro.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.component.GhostButton
import app.financepro.core.ui.component.SlushCard
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Slush
import app.financepro.core.ui.theme.SlushShapes
import app.financepro.core.ui.theme.Subheading
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind

/**
 * Categorias. REQ-CAT-001 · REQ-CAT-005 · REQ-UI-006
 *
 * A exclusão não oferece um "excluir" que falha: a tela pergunta ao banco
 * quantas transações estão presas **antes** de abrir a confirmação, e já mostra
 * para onde movê-las. Deixar o `RESTRICT` estourar e só então perguntar faria o
 * usuário passar por um erro para chegar à opção que resolve.
 */
@Composable
fun CategoriesScreen(vm: CategoriesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Slush.paper).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Categorias", style = Subheading, color = Slush.ink)
            GhostButton(text = "Nova", onClick = { vm.nova(CategoryKind.EXPENSE) })
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.categorias, key = { it.id }) { categoria ->
                Linha(
                    categoria = categoria,
                    onClick = { vm.editar(categoria) },
                    onExcluir = { vm.pedirExclusao(categoria) },
                )
            }
        }
    }

    state.editando?.let { categoria ->
        FormSheet(categoria, state.erro, vm::alterar, vm::salvar, vm::fechar)
    }

    state.excluindo?.let { pedido ->
        ExclusaoSheet(pedido, state.destinosPara(pedido.categoria), state.erro, vm)
    }
}

@Composable
private fun Linha(categoria: Category, onClick: () -> Unit, onExcluir: () -> Unit) {
    SlushCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Contorno pelo mesmo motivo de design.md §6.3: sem ele um ponto
            // Sunburst sobre papel branco dá 1.40:1 e some.
            Box(
                Modifier
                    .size(16.dp)
                    .clip(SlushShapes.extraSmall)
                    .background(Color(categoria.colorArgb))
                    .border(OutlineWidth, Slush.ink, SlushShapes.extraSmall),
            )
            Column(Modifier.weight(1f)) {
                Text(categoria.name, style = Body, color = Slush.ink, maxLines = 1)
                Text(
                    text = if (categoria.kind == CategoryKind.INCOME) "Receita" else "Despesa",
                    style = Caption,
                    color = Slush.ink,
                )
            }
            GhostButton(text = "Excluir", onClick = onExcluir)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormSheet(
    categoria: Category,
    erro: String?,
    onChange: (Category) -> Unit,
    onSalvar: () -> Unit,
    onDismiss: () -> Unit,
) {
    Folha(onDismiss) {
        OutlinedTextField(
            value = categoria.name,
            onValueChange = { onChange(categoria.copy(name = it)) },
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Natureza", style = Caption, color = Slush.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryKind.entries.forEach { kind ->
                val texto = if (kind == CategoryKind.INCOME) "Receita" else "Despesa"
                if (kind == categoria.kind) {
                    FilledCta(text = texto, onClick = { onChange(categoria.copy(kind = kind)) })
                } else {
                    GhostButton(text = texto, onClick = { onChange(categoria.copy(kind = kind)) })
                }
            }
        }
        if (erro != null) Text("⚠ $erro", style = Caption, color = Slush.ink)
        FilledCta(text = "Salvar", onClick = onSalvar, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExclusaoSheet(
    pedido: CategoriesState.Exclusao,
    destinos: List<Category>,
    erro: String?,
    vm: CategoriesViewModel,
) {
    Folha(vm::fechar) {
        Text(pedido.categoria.name, style = Subheading, color = Slush.ink)
        Text(pedido.mensagem, style = Body, color = Slush.ink)

        if (pedido.precisaDestino) {
            Text("Mover para", style = Caption, color = Slush.ink)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(destinos, key = { it.id }) { destino ->
                    if (destino.id == pedido.destino) {
                        FilledCta(text = destino.name, onClick = { vm.destinoDaExclusao(destino.id) })
                    } else {
                        GhostButton(text = destino.name, onClick = { vm.destinoDaExclusao(destino.id) })
                    }
                }
            }
        }

        if (erro != null) Text("⚠ $erro", style = Caption, color = Slush.ink)
        FilledCta(
            text = if (pedido.precisaDestino) "Mover e excluir" else "Excluir",
            onClick = vm::confirmarExclusao,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Folha(onDismiss: () -> Unit, conteudo: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SlushShapes.extraLarge,
        containerColor = Slush.paper,
        contentColor = Slush.ink,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            conteudo()
        }
    }
}
