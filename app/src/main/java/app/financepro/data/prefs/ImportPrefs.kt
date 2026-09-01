package app.financepro.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.financepro.data.ingest.MapeamentoCsv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O mapeamento de colunas de CSV, guardado por dialeto de banco. REQ-IMP-005
 *
 * DataStore e não tabela: são três inteiros por banco, sem relação com nada do
 * modelo, e uma tabela custaria entidade, DAO e migração para guardar o que cabe
 * numa string. Se um dia o mapeamento precisar aparecer numa tela de gestão, aí
 * ele vira tabela — e não antes.
 *
 * A chave é a assinatura da primeira linha do arquivo (`assinaturaCsv`), não o
 * nome dele: o extrato de setembro se chama diferente do de agosto, e o
 * cabeçalho do mesmo banco é o mesmo sempre.
 */
@Singleton
class ImportPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun mapeamentoDe(assinatura: String): MapeamentoCsv? {
        val bruto = context.dataStore.data.first()[chave(assinatura)] ?: return null
        val partes = bruto.split(SEPARADOR).mapNotNull { it.toIntOrNull() }
        return partes.takeIf { it.size == CAMPOS }?.let { MapeamentoCsv(it[0], it[1], it[2]) }
    }

    suspend fun lembrar(assinatura: String, mapa: MapeamentoCsv) {
        context.dataStore.edit {
            it[chave(assinatura)] = listOf(mapa.data, mapa.valor, mapa.descricao)
                .joinToString(SEPARADOR)
        }
    }

    private fun chave(assinatura: String) = stringPreferencesKey(PREFIXO + assinatura)

    private companion object {
        const val PREFIXO = "csv:"
        const val SEPARADOR = ","
        const val CAMPOS = 3
    }
}

private val Context.dataStore by preferencesDataStore(name = "import")
