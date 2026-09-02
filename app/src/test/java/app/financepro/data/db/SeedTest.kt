package app.financepro.data.db

import androidx.room.Room
import app.financepro.core.testing.Req
import app.financepro.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * REQ-CAT-004 · REQ-ACT-003
 *
 * Banco em **arquivo**, não em memória, porque a única coisa que este teste não
 * consegue provar num banco volátil é justamente a que importa: que o seed roda
 * na criação e **não** na reabertura.
 */
@Req("REQ-CAT-004", "REQ-ACT-003")
@RunWith(RobolectricTestRunner::class)
class SeedTest {

    private val app = RuntimeEnvironment.getApplication()
    private var db: AppDatabase? = null

    private fun abrir(): AppDatabase = Room
        .databaseBuilder(app, AppDatabase::class.java, "seed-test.db")
        .addCallback(AppDatabase.ForeignKeysOn)
        .addCallback(SeedCallback)
        .allowMainThreadQueries()
        .build()
        .also { db = it }

    @After
    fun fechar() {
        db?.close()
        app.deleteDatabase("seed-test.db")
    }

    @Test
    fun `semeia as dez categorias da spec`() = runBlocking {
        val nomes = abrir().categoryDao().observeActive().first().map { it.name }

        assertEquals(
            listOf(
                "Alimentação", "Transporte", "Moradia", "Saúde", "Lazer",
                "Educação", "Compras", "Assinaturas", "Salário", "Outros",
            ).sorted(),
            nomes.sorted(),
        )
    }

    @Test
    fun `semeia ao menos 40 regras de estabelecimento, todas apontando para categoria existente`() {
        val banco = abrir()
        val ids = CATEGORIAS_PADRAO.map { it.id }.toSet()

        assertTrue("esperava >= 40 regras, veio ${REGRAS_PADRAO.size}", REGRAS_PADRAO.size >= 40)
        assertEquals(emptyList<String>(), REGRAS_PADRAO.filter { it.second !in ids }.map { it.first })
        assertEquals(REGRAS_PADRAO.size, contar(banco, "payee_rule"))
    }

    @Test
    fun `chaves sao unicas e ja normalizadas`() {
        // Chave com acento ou minúscula nunca casaria com a saída de
        // `normalize` (T-036), e o erro só apareceria na primeira importação.
        val chaves = REGRAS_PADRAO.map { it.first }

        assertEquals(chaves.size, chaves.toSet().size)
        assertEquals(emptyList<String>(), chaves.filter { it != it.uppercase() })
        assertEquals(emptyList<String>(), chaves.filter { !it.matches(Regex("[A-Z0-9 ]+")) })
        assertEquals(emptyList<String>(), chaves.filter { it.contains(Regex("\\d{4,}")) })
    }

    @Test
    fun `cada despesa padrao tem a propria cor`() {
        // Já foram seis acentos para nove despesas, com três repetições
        // distribuídas o mais longe possível uma da outra. Numa lista isso passa,
        // porque o nome vem ao lado; no gráfico de pizza do relatório duas fatias
        // da mesma cor viram uma mancha só, e a legenda deixa de explicar qual é
        // qual — foi assim que o defeito apareceu, olhando o relatório.
        //
        // São nove acentos e nove despesas: a igualdade é o que garante que
        // acrescentar uma décima categoria padrão sem acrescentar uma cor faça
        // este teste falhar, em vez de reintroduzir a repetição em silêncio.
        val grid = CATEGORIAS_PADRAO.filter { it.kind == CategoryKind.EXPENSE }

        assertEquals(grid.size, grid.map { it.corArgb }.toSet().size)
    }

    @Test
    fun `reabrir o banco nao semeia de novo`() {
        val primeira = abrir()
        val categorias = contar(primeira, "category")
        val regras = contar(primeira, "payee_rule")
        primeira.close()

        val segunda = abrir()

        assertEquals(categorias, contar(segunda, "category"))
        assertEquals(regras, contar(segunda, "payee_rule"))
    }

    /** Tamanho da paleta de stickers (design.md §4). */

    private fun contar(banco: AppDatabase, tabela: String): Int =
        banco.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tabela").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
