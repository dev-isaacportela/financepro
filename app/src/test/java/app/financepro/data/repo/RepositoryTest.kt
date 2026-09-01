package app.financepro.data.repo

import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIA
import app.financepro.data.db.CONTA
import app.financepro.data.db.DbTest
import app.financepro.data.db.LANCAMENTO
import app.financepro.data.db.dia
import app.financepro.data.db.toDomain
import app.financepro.domain.model.AccountType
import app.financepro.domain.usecase.balanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A fronteira entidade ↔ domínio, que é onde os dois vocabulários podem
 * divergir sem ninguém notar.
 *
 * O teste vai até `balanceOf` de propósito: prova que o que sai do repositório
 * entra direto no caso de uso, sem adaptador no meio. Se o mapeamento perdesse
 * `cleared` ou `counterAccountId`, o saldo daria outro número aqui.
 */
class RepositoryTest : DbTest() {

    @Test
    fun `intervalo de datas atravessa a fronteira LocalDate - epochDay`() = runBlocking {
        val contas = AccountRepository(db.accountDao())
        val txns = TxnRepository(db.txnDao(), PayeeRuleRepository(db.payeeRuleDao()))
        val conta = db.accountDao().upsert(CONTA)
        val naConta = LANCAMENTO.copy(accountId = conta)
        db.txnDao().upsert(naConta.copy(date = dia(2026, 2, 28), description = "fora"))
        db.txnDao().upsert(naConta.copy(date = dia(2026, 3, 1), description = "borda"))
        db.txnDao().upsert(naConta.copy(date = dia(2026, 3, 31), description = "borda"))

        val marco = txns.observeBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).first()

        assertEquals(listOf("borda", "borda"), marco.map { it.description })
        assertEquals(LocalDate.of(2026, 3, 31), marco.first().date)
        assertEquals(conta, contas.byId(conta)?.id)
    }

    @Test
    fun `saldo calculado sobre o que o repositorio devolve fecha com a formula`() = runBlocking {
        val contas = AccountRepository(db.accountDao())
        val txns = TxnRepository(db.txnDao(), PayeeRuleRepository(db.payeeRuleDao()))
        val origem = db.accountDao().upsert(CONTA.copy(name = "Corrente", initialBalanceCents = 100_000))
        val destino = db.accountDao().upsert(CONTA.copy(name = "Poupança", type = AccountType.SAVINGS))
        val cat = db.categoryDao().upsert(CATEGORIA)
        db.txnDao().upsert(LANCAMENTO.copy(accountId = origem, categoryId = cat, amountCents = -18_50))
        db.txnDao().upsert(
            LANCAMENTO.copy(accountId = origem, counterAccountId = destino, amountCents = -30_000),
        )
        db.txnDao().upsert(LANCAMENTO.copy(accountId = origem, amountCents = -7_00, cleared = false))

        val todas = txns.observeBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).first()

        // 100.000 − 1.850 − 30.000; o previsto de 700 não entra (REQ-TXN-006).
        assertEquals(68_150L, balanceOf(contas.byId(origem)!!, todas))
        // A transferência chega no destino pelo segundo termo da fórmula.
        assertEquals(30_000L, balanceOf(contas.byId(destino)!!, todas))
    }

    /**
     * REQ-TXN-010 — o desfazer devolve a linha **inteira**.
     *
     * É o teste que justifica o repositório guardar a `TxnEntity` em vez de o
     * ViewModel guardar o `Txn`: `notes`, `dedupeKey`, `importBatchId` e
     * `createdAt` não existem no modelo de domínio, e um desfazer que os apaga
     * é pior que não ter desfazer — a `dedupeKey` perdida faria a próxima
     * importação recriar a transação como se fosse nova.
     *
     * Um `assertEquals` do objeto inteiro, e não campo a campo: assim ele
     * continua pegando o erro quando alguém acrescentar a décima segunda coluna.
     */
    @Req("REQ-TXN-010")
    @Test
    fun `desfazer repoe a linha inteira, com id e colunas que o dominio nao carrega`() = runBlocking {
        val txns = TxnRepository(db.txnDao(), PayeeRuleRepository(db.payeeRuleDao()))
        val conta = db.accountDao().upsert(CONTA)
        val id = db.txnDao().insert(
            LANCAMENTO.copy(
                accountId = conta,
                description = "Padaria",
                notes = "com o troco",
                dedupeKey = "OFX-42",
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_000_000,
            ),
        )
        val original = db.txnDao().byId(id)

        txns.excluirVarias(listOf(id))
        assertNull(db.txnDao().byId(id))

        assertTrue(txns.desfazerExclusao())

        assertEquals(original, db.txnDao().byId(id))
    }

    /**
     * REQ-TXN-001 — editar **atualiza**, e não perde o que o domínio não carrega.
     *
     * Os dois erros que este teste existe para pegar são invisíveis na revisão:
     * um `insert` no lugar do `update` duplica dinheiro na tela, e uma entidade
     * montada do zero apaga `notes`, `dedupeKey`, `importBatchId` e
     * `recurringRuleId` num `UPDATE` que retorna sucesso.
     *
     * `importBatchId` e `recurringRuleId` ficam nulos porque as tabelas-pai só
     * ganham DAO na F1/F2 — mas o `assertEquals` do objeto **inteiro** cobre as
     * duas do mesmo jeito: código que as sobrescrevesse falharia a igualdade.
     */
    @Req("REQ-TXN-001")
    @Test
    fun `salvar sobre id existente atualiza a linha e preserva o que o dominio nao carrega`() =
        runBlocking {
            val txns = TxnRepository(db.txnDao(), PayeeRuleRepository(db.payeeRuleDao()))
            val conta = db.accountDao().upsert(CONTA)
            val id = db.txnDao().insert(
                LANCAMENTO.copy(
                    accountId = conta,
                    description = "Padria",
                    notes = "com o troco",
                    dedupeKey = "OFX-42",
                    createdAt = 1_700_000_000_000,
                    updatedAt = 1_700_000_000_000,
                ),
            )
            val original = db.txnDao().byId(id)!!

            txns.salvar(original.toDomain().copy(description = "Padaria", amountCents = -12_00))

            val depois = db.txnDao().byId(id)!!
            assertEquals(
                original.copy(
                    description = "Padaria",
                    amountCents = -12_00,
                    updatedAt = depois.updatedAt,
                ),
                depois,
            )
            // `createdAt` é do nascimento da linha; `updatedAt` é do toque de agora.
            assertEquals(1_700_000_000_000, depois.createdAt)
            assertTrue(depois.updatedAt > original.updatedAt)
            // Uma linha, não duas.
            assertEquals(1, txns.observeTudo().first().size)
        }

    @Test
    fun `desfazer duas vezes repoe uma vez, e sem exclusao pendente nao faz nada`() = runBlocking {
        val txns = TxnRepository(db.txnDao(), PayeeRuleRepository(db.payeeRuleDao()))
        val conta = db.accountDao().upsert(CONTA)
        val id = db.txnDao().insert(LANCAMENTO.copy(accountId = conta))

        // Sem nada pendente: no-op, e não um `insert` de lixo.
        assertFalse(txns.desfazerExclusao())

        txns.excluirVarias(listOf(id))
        assertTrue(txns.desfazerExclusao())
        // O segundo desfazer não pode duplicar a linha — "desfazer" repõe uma vez.
        assertFalse(txns.desfazerExclusao())

        val todas = txns.observeBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).first()
        assertEquals(1, todas.size)
    }
}
