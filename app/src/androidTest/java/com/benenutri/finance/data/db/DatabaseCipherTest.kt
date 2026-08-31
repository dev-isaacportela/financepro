package com.benenutri.finance.data.db

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.benenutri.finance.domain.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * REQ-SEC-001 · REQ-SEC-002 — o banco cifrado, no aparelho.
 *
 * A spec marca os dois como `Teste: manual`, e por um bom motivo: SQLCipher é
 * biblioteca nativa e o Android Keystore é hardware, então nada disso existe na
 * JVM. O que este teste faz é trocar o ritual manual — extrair o `.db` por adb e
 * abrir no `sqlite3` — por uma verificação repetível, que roda de novo a cada
 * mudança em vez de uma vez, no dia em que alguém lembrar.
 *
 * O caminho exercido é o de produção: [buildDatabase], o mesmo que o módulo Hilt
 * chama. Um teste que montasse o banco de outro jeito provaria a criptografia de
 * um banco que ninguém abre desse jeito.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseCipherTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val arquivoDb: File get() = context.getDatabasePath(AppDatabase.NAME)
    private val arquivoChave: File get() = File(context.filesDir, "db.key")

    @Before
    fun limpar() = apagarTudo()

    @After
    fun limparDepois() = apagarTudo()

    @Test
    fun bancoAbreSemeadoEEstaCifradoEmDisco() = runBlocking {
        val db = buildDatabase(context)
        assertEquals(CATEGORIAS_PADRAO.size, db.categoryDao().observeActive().first().size)
        db.close()

        assertTrue("o arquivo do banco nem foi criado", arquivoDb.exists())

        // Um SQLite em claro começa com "SQLite format 3". É a marca que o
        // `sqlite3` procura, e é exatamente ela que o SQLCipher não deixa aparecer.
        val cabecalho = arquivoDb.readBytes().take(MAGIC.size).toByteArray()
        assertFalse(
            "o banco está em claro: começa com \"SQLite format 3\"",
            cabecalho.contentEquals(MAGIC),
        )
    }

    @Test
    fun reabrirComAMesmaChaveEnxergaOsMesmosDados() = runBlocking {
        // É o que prova que a senha sobrevive ao ciclo Keystore: gerar, embrulhar,
        // gravar, ler, desembrulhar. Se o desembrulho falhasse, o banco não abriria
        // — que é o modo de falha que faria o usuário perder o histórico inteiro.
        val primeira = buildDatabase(context)
        val conta = primeira.accountDao().upsert(
            AccountEntity(name = "Corrente", type = AccountType.CHECKING, colorArgb = 0, iconKey = "wallet"),
        )
        primeira.close()

        val segunda = buildDatabase(context)
        assertEquals("Corrente", segunda.accountDao().byId(conta)?.name)
        assertEquals(CATEGORIAS_PADRAO.size, segunda.categoryDao().observeActive().first().size)
        segunda.close()
    }

    @Test
    fun aSenhaNaoFicaEmClaroEmLugarNenhum() = runBlocking {
        // A consulta não é enfeite: o Room só abre o arquivo na primeira query,
        // e sem ela o banco e o envelope da chave nem chegariam a existir.
        buildDatabase(context).also { it.categoryDao().byId(1); it.close() }

        // O envelope é IV(12) + senha(32) + tag GCM(16). Se alguém trocasse o
        // embrulho por uma gravação crua, o arquivo teria 32 bytes.
        assertTrue("db.key não existe", arquivoChave.exists())
        assertEquals("db.key não tem o tamanho de um envelope AES-GCM", ENVELOPE, arquivoChave.length())

        // REQ-SEC-002 nomeia SharedPreferences porque é onde esse tipo de segredo
        // costuma parar. O diretório nem deve existir.
        val prefs = File(context.dataDir, "shared_prefs")
        assertFalse("apareceu shared_prefs: ${prefs.list()?.toList()}", prefs.exists())
    }

    private fun apagarTudo() {
        context.deleteDatabase(AppDatabase.NAME)
        arquivoChave.delete()
    }

    private companion object {
        /** Os 15 primeiros bytes de todo SQLite em claro. O 16o e NUL, e um
         * byte nulo dentro de fonte Kotlin e problema de quem for abrir o
         * arquivo depois -- 15 bastam para distinguir claro de cifrado. */
        val MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        const val ENVELOPE = 12L + 32L + 16L
    }
}
