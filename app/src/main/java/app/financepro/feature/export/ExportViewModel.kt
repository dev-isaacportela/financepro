package app.financepro.feature.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.export.ExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * O que a tela de exportação precisa saber. REQ-BAK-001
 *
 * [recado] é o resultado da última tentativa, e é o estado inteiro: a tela tem
 * dois botões e nada mais para guardar. Um `sealed class` de três casos aqui
 * seria a abstração especulativa que o Art. 10 proíbe — a frase já é o dado.
 */
data class ExportState(val recado: String? = null, val trabalhando: Boolean = false)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportacao: ExportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    fun exportarCsv(destino: Uri) = exportar("transações") { exportacao.exportarCsv(destino) }

    fun exportarJson(destino: Uri) = exportar("registros") { exportacao.exportarJson(destino) }

    /**
     * A falha vira **frase na tela**, não exceção que derruba o app.
     *
     * Escrita em `Uri` de outro app falha por motivos que não são bug nosso —
     * cartão removido, provedor de nuvem sem espaço, permissão revogada entre a
     * escolha e a gravação. Quem exporta o histórico financeiro precisa saber
     * que **não** foi salvo; um crash diria isso do jeito mais confuso possível.
     */
    private fun exportar(unidade: String, bloco: suspend () -> Int) = viewModelScope.launch {
        _state.value = ExportState(trabalhando = true)
        val recado = runCatching { bloco() }
            .fold(
                onSuccess = { "Pronto: $it $unidade no arquivo." },
                onFailure = { "Não deu para salvar: ${it.message ?: "erro desconhecido"}" },
            )
        _state.value = ExportState(recado = recado)
    }
}
