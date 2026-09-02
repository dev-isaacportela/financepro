package app.financepro.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * As migrações, exercidas contra o schema exportado. REQ-DATA-001 · Art. 12
 *
 * O [MigrationTestHelper] cria o banco **na versão antiga** a partir do JSON de
 * `app/schemas/`, e não a partir das entidades de agora. É o que dá sentido ao
 * teste: a migração roda sobre o DDL que está de fato instalado no aparelho de
 * alguém, e `runMigrationsAndValidate` reprova se o resultado não bater, coluna
 * por coluna, com o schema da versão de destino.
 *
 * **O que se testa é o `WHERE`, não o `UPDATE`.** As duas primeiras migrações
 * repintam cor, e a parte difícil delas nunca foi trocar um valor — foi não
 * trocar o valor de quem escolheu a cor à mão. Por isso cada caso insere duas
 * linhas: uma que a migração deve mexer e outra que ela não pode encostar.
 *
 * Roda em JVM, via Robolectric: migração testada em segundos, o que é a
 * diferença entre rodá-la a cada commit e rodá-la depois do bug.
 */
@RunWith(RobolectricTestRunner::class)
@Req("REQ-DATA-001")
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File(RuntimeEnvironment.getTempDirectory().create("migracao").toFile(), BANCO),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun `1 para 2 repinta a paleta antiga e deixa a cor escolhida em paz`() {
        helper.createDatabase(1).use { db ->
            db.categoria(id = 1, nome = "Alimentação", cor = ELECTRIC_BLUE)
            db.categoria(id = 2, nome = "Escolhida à mão", cor = COR_DO_USUARIO)
            db.conta(id = 1, nome = "Carteira", cor = MINT_POP)
        }

        helper.runMigrationsAndValidate(2, listOf(DE_1_PARA_2)).use { db ->
            assertEquals(AZUL, db.corDaCategoria(1))
            assertEquals("cor escolhida pelo usuário foi sobrescrita", COR_DO_USUARIO, db.corDaCategoria(2))
            assertEquals(VERDE_AZULADO, db.corDaConta(1))
        }
    }

    @Test
    fun `2 para 3 alcanca os dois grupos de instalacao, e so os ids do seed`() {
        // A armadilha desta migração: cada id de seed tem **duas** cores "de
        // antes" — a de quem instalou depois da troca de paleta, e a que
        // [DE_1_PARA_2] produziu em quem já tinha o app. Casar só com a primeira
        // deixaria metade das instalações com a repetição que a migração existe
        // para acabar, em silêncio.
        helper.createDatabase(2).use { db ->
            db.categoria(id = 4, nome = "Saúde", cor = AZUL) // veio da v1
            db.categoria(id = 5, nome = "Lazer", cor = ROSA) // instalação nova
            db.categoria(id = 42, nome = "Pets", cor = AZUL) // criada pelo usuário
        }

        helper.runMigrationsAndValidate(3, listOf(DE_2_PARA_3)).use { db ->
            assertEquals(MARROM, db.corDaCategoria(4))
            assertEquals(LARANJA, db.corDaCategoria(5))
            assertEquals("categoria do usuário foi repintada", AZUL, db.corDaCategoria(42))
        }
    }

    @Test
    fun `3 para 4 abre indexador e taxa sem tocar no que ja estava gravado`() {
        helper.createDatabase(3).use { db ->
            db.conta(id = 1, nome = "Reserva", cor = AZUL)
        }

        helper.runMigrationsAndValidate(4, listOf(DE_3_PARA_4)).use { db ->
            db.prepare("SELECT name, indexador, taxaBp FROM account WHERE id = 1").use { linha ->
                assertTrue(linha.step())
                assertEquals("Reserva", linha.getText(0))
                assertTrue("conta antiga nasceu com indexador", linha.isNull(1))
                assertTrue("conta antiga nasceu com taxa", linha.isNull(2))
            }
        }
    }

    @Test
    fun `a escada inteira sobe da 1 ate a 4 sem perder linha`() {
        // Cada salto tem seu caso acima; este prova que eles compõem. É o
        // caminho de quem instalou na primeira versão e só agora atualizou — o
        // único que roda as três migrações em sequência, e o que ninguém
        // exercita à mão.
        helper.createDatabase(1).use { db ->
            db.categoria(id = 1, nome = "Alimentação", cor = ELECTRIC_BLUE)
            db.conta(id = 1, nome = "Carteira", cor = MINT_POP)
        }

        helper.runMigrationsAndValidate(4, listOf(DE_1_PARA_2, DE_2_PARA_3, DE_3_PARA_4)).use { db ->
            assertEquals(1, db.contar("category"))
            assertEquals(1, db.contar("account"))
            // Alimentação: Electric Blue → Azul na 1→2, e Azul → Verde-azulado
            // na 2→3, que é justamente o segundo grupo de cores do `WHERE`.
            assertEquals(VERDE_AZULADO, db.corDaCategoria(1))
        }
    }

    // Fixture, e não DAO: os literais entram direto no SQL porque nada aqui vem
    // de fora do arquivo. Ligar o Room a um banco em versão antiga é justamente
    // o que não dá para fazer.
    private fun SQLiteConnection.categoria(id: Long, nome: String, cor: Long) = execSQL(
        "INSERT INTO category (id, name, kind, parentId, iconKey, colorArgb, archived, useCount) " +
            "VALUES ($id, '$nome', 'EXPENSE', NULL, 'dots', $cor, 0, 0)",
    )

    private fun SQLiteConnection.conta(id: Long, nome: String, cor: Long) = execSQL(
        "INSERT INTO account (id, name, type, initialBalanceCents, colorArgb, iconKey, archived, sortOrder) " +
            "VALUES ($id, '$nome', 'CASH', 0, $cor, 'wallet', 0, 0)",
    )

    private fun SQLiteConnection.corDaCategoria(id: Long): Long = umLong("category", id)

    private fun SQLiteConnection.corDaConta(id: Long): Long = umLong("account", id)

    private fun SQLiteConnection.umLong(tabela: String, id: Long): Long =
        prepare("SELECT colorArgb FROM $tabela WHERE id = $id").use { it.step(); it.getLong(0) }

    private fun SQLiteConnection.contar(tabela: String): Long =
        prepare("SELECT COUNT(*) FROM $tabela").use { it.step(); it.getLong(0) }

    private companion object {
        const val BANCO = "migracao-teste"

        // Os literais são dado congelado, como nas próprias migrações: o que
        // está gravado no banco de alguém, não o que a paleta viva diz hoje.
        const val ELECTRIC_BLUE = -11689217L
        const val MINT_POP = -11150436L
        const val AZUL = -16745534L
        const val VERDE_AZULADO = -16734082L
        const val ROSA = -1696183L
        const val MARROM = -7115422L
        const val LARANJA = -1278464L

        /** Fora da paleta em qualquer versão: nenhuma migração pode casar com ela. */
        const val COR_DO_USUARIO = -6710887L
    }
}
