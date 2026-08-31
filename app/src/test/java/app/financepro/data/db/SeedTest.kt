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
    fun `nenhuma categoria vizinha no grid repete a cor`() {
        // São seis stickers para dez categorias, então repetir é inevitável — o
        // que não pode é repetir *lado a lado*. Enquanto todo `useCount` é zero
        // o grid sai em ordem alfabética, que é o que o usuário novo vê.
        val grid = CATEGORIAS_PADRAO
            .filter { it.kind == CategoryKind.EXPENSE }
            .sortedBy { it.nome }

        val vizinhasIguais = grid.zipWithNext()
            .filter { (a, b) -> a.corArgb == b.corArgb }
            .map { (a, b) -> "${a.nome} e ${b.nome}" }

        assertEquals(emptyList<String>(), vizinhasIguais)
    }

    @Test
    fun `as cores repetidas ficam o mais longe possivel`() {
        // Com 6 cores e 9 despesas, a distância máxima possível entre duas
        // iguais é 6. Menos que isso significa que alguém atribuiu por id de
        // novo, em vez de pela ordem em que o grid aparece.
        val grid = CATEGORIAS_PADRAO
            .filter { it.kind == CategoryKind.EXPENSE }
            .sortedBy { it.nome }

        val perto = grid.indices.flatMap { i ->
            (i + 1 until grid.size)
                .filter { j -> grid[i].corArgb == grid[j].corArgb && j - i < ciclo }
                .map { j -> "${grid[i].nome} e ${grid[j].nome} a ${j - i} posições" }
        }

        assertEquals(emptyList<String>(), perto)
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
    private val ciclo = 6

    private fun contar(banco: AppDatabase, tabela: String): Int =
        banco.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tabela").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
