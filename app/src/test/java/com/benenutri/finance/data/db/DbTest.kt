package com.benenutri.finance.data.db

import androidx.room.Room
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
}
