package app.financepro.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A preferência de bloqueio do app. REQ-SEC-003 · REQ-SEC-005
 *
 * DataStore e não `SharedPreferences` porque a camada já está desenhada assim
 * em arquitetura.md §3 (`Repository(Flow) → DAO/DataStore`): a UI consome
 * `Flow`, e ligar o bloqueio precisa aplicar `FLAG_SECURE` na hora, não no
 * próximo start. Com `SharedPreferences` isso custaria um `callbackFlow`.
 *
 * Sem interface: uma implementação só (Art. 10). Hilt troca o módulo inteiro
 * quando um teste precisar de outro comportamento.
 *
 * **Não guarda segredo.** É um booleano de preferência, não a chave nem o
 * hash de nada — a chave do banco mora no Keystore (REQ-SEC-002), e quem
 * desligar isto por fora ainda não abre o banco.
 */
@Singleton
class SecurityPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** `false` até a primeira gravação: bloqueio é opcional, e opcional nasce desligado. */
    val bloqueio: Flow<Boolean> = context.dataStore.data.map { it[BLOQUEIO] == true }

    suspend fun definirBloqueio(ativo: Boolean) {
        context.dataStore.edit { it[BLOQUEIO] = ativo }
    }

    private companion object {
        val BLOQUEIO = booleanPreferencesKey("bloqueio_biometrico")
    }
}

private val Context.dataStore by preferencesDataStore(name = "security")
