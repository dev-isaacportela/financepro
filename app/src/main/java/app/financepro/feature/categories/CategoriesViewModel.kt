package app.financepro.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.toArgb
import app.financepro.core.ui.theme.LightBlue
import app.financepro.data.repo.CategoryRepository
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Categorias, com a exclusão protegida de REQ-CAT-005.
 *
 * [excluindo] carrega a contagem de transações presas: a mensagem da spec é
 * "Mova as N transações antes", e um N é o que diferencia um aviso acionável de
 * um "não foi possível".
 */
data class CategoriesState(
    val categorias: List<Category> = emptyList(),
    val editando: Category? = null,
    val excluindo: Exclusao? = null,
    val erro: String? = null,
) {
    /** A categoria a excluir, e quantas transações a seguram. */
    data class Exclusao(val categoria: Category, val presas: Int, val destino: Long? = null) {
        val precisaDestino: Boolean get() = presas > 0
        val mensagem: String
            get() = if (precisaDestino) "Mova as $presas transações antes" else "Excluir de vez?"
    }

    fun destinosPara(alvo: Category): List<Category> =
        categorias.filter { it.id != alvo.id && it.kind == alvo.kind && !it.archived }
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categorias: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categorias.observeActive().collect { cs -> _state.update { it.copy(categorias = cs) } }
        }
    }

    fun nova(kind: CategoryKind) = _state.update {
        it.copy(
            editando = Category(id = 0, name = "", kind = kind, colorArgb = PADRAO, iconKey = "tag"),
            erro = null,
        )
    }

    fun editar(categoria: Category) = _state.update { it.copy(editando = categoria, erro = null) }

    fun alterar(categoria: Category) = _state.update { it.copy(editando = categoria, erro = null) }

    fun fechar() = _state.update { it.copy(editando = null, excluindo = null, erro = null) }

    fun salvar() {
        val categoria = _state.value.editando ?: return
        if (categoria.name.isBlank()) {
            _state.update { it.copy(erro = "Dê um nome à categoria") }
            return
        }
        viewModelScope.launch {
            // `salvar` passa por `upsertChecked`, então subcategoria de
            // subcategoria é recusada aqui e não vira estado inválido no banco.
            runCatching { categorias.salvar(categoria) }
                .onSuccess { _state.update { it.copy(editando = null, erro = null) } }
                .onFailure { e -> _state.update { it.copy(erro = e.message) } }
        }
    }

    /**
     * Abre a confirmação já sabendo quantas transações estão presas.
     *
     * Perguntar antes de tentar excluir é o que permite oferecer o destino na
     * mesma tela. Tentar, falhar no `RESTRICT` e só então perguntar faria o
     * usuário passar por um erro para chegar à opção que resolve.
     */
    fun pedirExclusao(categoria: Category) = viewModelScope.launch {
        val presas = categorias.transacoesEm(categoria.id)
        _state.update { it.copy(excluindo = CategoriesState.Exclusao(categoria, presas)) }
    }

    fun destinoDaExclusao(id: Long) = _state.update {
        it.copy(excluindo = it.excluindo?.copy(destino = id))
    }

    fun confirmarExclusao() {
        val pedido = _state.value.excluindo ?: return
        if (pedido.precisaDestino && pedido.destino == null) {
            _state.update { it.copy(erro = pedido.mensagem) }
            return
        }
        viewModelScope.launch {
            runCatching { categorias.excluir(pedido.categoria, pedido.destino) }
                .onSuccess { _state.update { it.copy(excluindo = null, erro = null) } }
                .onFailure { e -> _state.update { it.copy(erro = e.message) } }
        }
    }

    private companion object {
        /**
         * Categoria nova nasce neutra até alguém escolher.
         *
         * Vem do token, não de um hexadecimal aqui: `feature/` pode importar o
         * tema, e uma cor solta seria exatamente o que REQ-DS-001 proíbe — só
         * que num formato que a varredura de `Color(0x` não pegaria.
         */
        val PADRAO = LightBlue.toArgb()
    }
}
