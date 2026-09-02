package app.financepro.data.indices

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O CDI guardado. REQ-INV-005 · REQ-INV-006
 *
 * [em] é a data **do dado**, não a da busca: o BCB publica em dia útil, então
 * numa segunda-feira o valor mais recente é o de sexta. Mostrar "atualizado
 * hoje" num número de três dias atrás seria mentir sobre a coisa que o usuário
 * está conferindo.
 *
 * [manual] separa o que veio da rede do que a pessoa digitou — a tela diz qual
 * é qual, e não há como confundir um palpite com a taxa oficial.
 */
data class Cdi(val anualBp: Int, val em: LocalDate, val manual: Boolean)

/**
 * O último CDI conhecido, em DataStore. REQ-INV-005 · REQ-INV-006
 *
 * DataStore e não tabela, pela mesma razão de [app.financepro.data.prefs.ImportPrefs]:
 * são três valores sem relação nenhuma com o modelo, e uma tabela custaria
 * entidade, DAO e migração para guardar o que cabe em três chaves.
 *
 * **É o que faz o app continuar inteiro sem rede.** O valor sobrevive ao modo
 * avião, à reinstalação do Wi-Fi e ao servidor do BCB fora do ar; a tela mostra
 * o número com a data dele, e quem quiser corrigir digita por cima.
 *
 * Uma busca bem-sucedida sempre vence o valor digitado — foi a rede que o
 * usuário escolheu como fonte, e o campo manual é ponte para quem está sem ela,
 * não configuração para desligá-la.
 */
@Singleton
class IndicesPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun observar(): Flow<Cdi?> = context.dataStore.data.map { p ->
        val bp = p[CDI_BP] ?: return@map null
        val dia = p[CDI_EM] ?: return@map null
        Cdi(anualBp = bp, em = LocalDate.ofEpochDay(dia), manual = p[CDI_MANUAL] == true)
    }

    suspend fun guardar(cdi: Cdi) {
        context.dataStore.edit {
            it[CDI_BP] = cdi.anualBp
            it[CDI_EM] = cdi.em.toEpochDay()
            it[CDI_MANUAL] = cdi.manual
        }
    }

    private companion object {
        val CDI_BP = intPreferencesKey("cdi_anual_bp")
        val CDI_EM = longPreferencesKey("cdi_em_epoch_day")
        val CDI_MANUAL = booleanPreferencesKey("cdi_manual")
    }
}

private val Context.dataStore by preferencesDataStore(name = "indices")
