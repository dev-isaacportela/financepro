package app.financepro.data.export

import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIA
import app.financepro.data.db.CONTA
import app.financepro.data.db.DbTest
import app.financepro.data.db.LANCAMENTO
import app.financepro.data.db.dia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-BAK-002 · REQ-BAK-003 · REQ-BAK-004
 *
 * O backup é a cópia que sobra quando o aparelho se perde, e a restauração é a
 * única operação do app que apaga tudo de propósito. As duas erram de formas que
 * ninguém percebe na hora: senha errada que devolve lixo em vez de erro, arquivo
 * adulterado que passa, restauração que deixa a base pela metade, e apagar tudo
 * que devolve um app sem categoria nenhuma.
 */
@Req("REQ-BAK-002", "REQ-BAK-003", "REQ-BAK-004")
class BackupTest : DbTest() {

    private val senha = "senha-bem-comprida".toCharArray()

    private val base = BaseExportada(
        accounts = listOf(CONTA.copy(id = 1, name = "Conta corrente")),
        categories = listOf(CATEGORIA.copy(id = 10, name = "Alimentação")),
        txns = listOf(
            LANCAMENTO.copy(
                id = 100,
                accountId = 1,
                categoryId = 10,
                amountCents = -1_234_56,
                date = dia(2026, 3, 7),
                description = "Padaria",
                dedupeKey = "OFX-7",
            ),
        ),
    )

    // ---------- REQ-BAK-002: o arquivo ----------

    @Test
    fun `o backup volta igual, campo a campo`() {
        val voltou = decifrarBackup(cifrarBackup(base, senha), senha)

        assertEquals(base, voltou)
        assertEquals("OFX-7", voltou.txns.single().dedupeKey)
    }

    @Test
    fun `senha errada e erro, nao lixo`() {
        val arquivo = cifrarBackup(base, senha)

        // Sem GCM isto devolveria bytes aleatórios, e o parser de JSON
        // estouraria com uma mensagem sobre sintaxe — para quem está tentando
        // recuperar o próprio histórico, "senha incorreta" é a única frase útil.
        val erro = assertThrows(BackupIlegivel::class.java) {
            decifrarBackup(arquivo, "outra-senha-longa".toCharArray())
        }
        assertTrue(erro.message.orEmpty().contains("Senha incorreta"))
    }

    @Test
    fun `um byte trocado no meio derruba a leitura`() {
        val arquivo = cifrarBackup(base, senha)
        arquivo[arquivo.size - 1] = (arquivo.last() + 1).toByte()

        assertThrows(BackupIlegivel::class.java) { decifrarBackup(arquivo, senha) }
    }

    @Test
    fun `um byte trocado no cabecalho tambem derruba`() {
        // O cabeçalho vai em claro, e é por isso que ele entra como AAD: sem
        // isso, mexer no sal daria "senha errada" — e mexer nele é exatamente o
        // que um arquivo corrompido faz.
        val arquivo = cifrarBackup(base, senha)
        val posicaoDoSal = 5
        arquivo[posicaoDoSal] = (arquivo[posicaoDoSal] + 1).toByte()

        assertThrows(BackupIlegivel::class.java) { decifrarBackup(arquivo, senha) }
    }

    @Test
    fun `arquivo que nao e backup e recusado antes de derivar a chave`() {
        val erro = assertThrows(BackupIlegivel::class.java) {
            decifrarBackup("nem de longe um backup".toByteArray(), senha)
        }

        assertTrue(erro.message.orEmpty().contains("não é um backup"))
    }

    @Test
    fun `dois backups da mesma base sao arquivos diferentes`() {
        // Sal e IV sorteados por arquivo. Iguais, dois backups da mesma base
        // sairiam byte a byte idênticos, e quem visse os dois saberia que nada
        // mudou entre eles.
        val um = cifrarBackup(base, senha)
        val outro = cifrarBackup(base, senha)

        assertNotEquals(um.toList(), outro.toList())
        assertEquals(base, decifrarBackup(outro, senha))
    }

    @Test
    fun `o arquivo cifrado nao carrega a descricao em claro`() {
        val arquivo = cifrarBackup(base, senha)

        assertFalse("Padaria" in String(arquivo.map { it.toInt().toChar() }.toCharArray()))
    }

    // ---------- REQ-BAK-003: a troca da base ----------

    @Test
    fun `restaurar troca a base inteira numa transacao`() = runBlocking {
        val dao = db.backupDao()
        val conta = db.accountDao().upsert(CONTA.copy(name = "Antiga"))
        db.txnDao().insert(LANCAMENTO.copy(accountId = conta, description = "Some"))

        dao.substituir(
            contas = base.accounts,
            categorias = base.categories,
            regras = base.recurringRules,
            tetos = base.budgets,
            txns = base.txns,
        )

        assertEquals(listOf("Conta corrente"), db.accountDao().todas().map { it.name })
        assertEquals(listOf("Padaria"), db.txnDao().todas().map { it.description })
        // O id volta como estava no arquivo, senão as transações apontariam
        // para uma conta que a restauração renumerou.
        assertEquals(1L, db.accountDao().todas().single().id)
    }

    @Test
    fun `restaurar de um backup vazio deixa a base vazia`() = runBlocking {
        val conta = db.accountDao().upsert(CONTA)
        db.txnDao().insert(LANCAMENTO.copy(accountId = conta))

        db.backupDao().substituir(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertTrue(db.txnDao().todas().isEmpty())
        assertTrue(db.accountDao().todas().isEmpty())
    }

    // ---------- REQ-BAK-004: apagar tudo ----------

    @Test
    fun `apagar tudo devolve o app ao estado de instalacao`() = runBlocking {
        val conta = db.accountDao().upsert(CONTA)
        db.txnDao().insert(LANCAMENTO.copy(accountId = conta))

        db.backupDao().apagarTudo()

        assertTrue("sobrou transação", db.txnDao().todas().isEmpty())
        assertTrue("sobrou conta", db.accountDao().todas().isEmpty())
        // As categorias voltam: a semente roda em `onCreate`, e o arquivo
        // continua existindo depois do DELETE. Sem repô-las, o usuário voltaria
        // ao onboarding com o grid de lançamento vazio.
        assertEquals(CATEGORIAS_SEMEADAS, db.categoryDao().todas().size)
    }

    @Test
    fun `apagar tudo duas vezes seguidas nao duplica as categorias`() = runBlocking {
        db.backupDao().apagarTudo()
        db.backupDao().apagarTudo()

        assertEquals(CATEGORIAS_SEMEADAS, db.categoryDao().todas().size)
    }

    private companion object {
        /** Quantas a semente cria. Ver `CATEGORIAS_PADRAO` em `Seed.kt`. */
        const val CATEGORIAS_SEMEADAS = 10
    }
}
