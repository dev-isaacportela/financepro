package app.financepro.data.db

import android.os.Looper
import androidx.room.Room
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Base dos testes de DAO.
 *
 * Banco **em memória**, aberto pelo mesmo [AppDatabase.ForeignKeysOn] que a
 * produção usa. Se o callback fosse só do módulo Hilt, o teste provaria que
 * `RESTRICT` funciona num banco que ninguém abre desse jeito.
 *
 * Roda em JVM via Robolectric: DAO testado em segundos, sem emulador — que é o
 * que faz o teste ser rodado antes de cada commit em vez de uma vez por sprint.
 * O SQLCipher (T-005) não entra aqui: ele troca o `openHelperFactory`, não o
 * SQL, e exige biblioteca nativa que não existe na JVM.
 */
@RunWith(RobolectricTestRunner::class)
abstract class DbTest {

    protected lateinit var db: AppDatabase

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.ForeignKeysOn)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    /**
     * Espera um `StateFlow` alcançar a condição, girando o looper do Robolectric.
     *
     * Doze linhas em vez de `coroutines-test` + `turbine`: nenhuma das duas está
     * no `build.gradle.kts`, e acrescentar dependência para o que isto já faz é
     * o que o Art. 10 recusa. Nasceu privada no `CategoriesViewModelTest` (T-016)
     * e subiu quando o `TransactionsViewModelTest` (T-014) virou o segundo
     * chamador — a mesma regra que autoriza qualquer promoção aqui.
     */
    protected fun esperar(descricao: String, condicao: () -> Boolean) {
        val limite = System.nanoTime() + LIMITE_NANOS
        while (System.nanoTime() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicao()) return
            Thread.sleep(PASSO_MS)
        }
        error("tempo esgotado esperando: $descricao")
    }

    private companion object {
        const val LIMITE_NANOS = 5_000_000_000L
        const val PASSO_MS = 5L
    }
}
