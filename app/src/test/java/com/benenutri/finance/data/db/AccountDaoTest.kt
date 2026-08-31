package com.benenutri.finance.data.db

import com.benenutri.finance.core.testing.Req
import com.benenutri.finance.domain.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Req("REQ-ACC-001", "REQ-ACC-002")
class AccountDaoTest : DbTest() {

    private val dao get() = db.accountDao()

    @Test
    fun `cria edita e lista conta`() = runBlocking {
        val id = dao.upsert(CONTA.copy(name = "Nubank", initialBalanceCents = 250_00))

        assertEquals("Nubank", dao.byId(id)?.name)

        dao.upsert(dao.byId(id)!!.copy(name = "Nubank PJ"))
        assertEquals("Nubank PJ", dao.byId(id)?.name)
        assertEquals(listOf("Nubank PJ"), dao.observeActive().first().map { it.name })
    }

    @Test
    fun `os cinco tipos sobrevivem ao round-trip`() = runBlocking {
        // Enum gravado como TEXT: o teste falharia se alguém trocasse por
        // ordinal e depois reordenasse as constantes.
        AccountType.entries.forEach { type ->
            val id = dao.upsert(CONTA.copy(name = type.name, type = type))
            assertEquals(type, dao.byId(id)?.type)
        }
        assertEquals(AccountType.entries.size, dao.observeAll().first().size)
    }

    @Test
    fun `campos de cartao ficam nulos nos demais tipos`() = runBlocking {
        val corrente = dao.upsert(CONTA)
        val cartao = dao.upsert(
            CONTA.copy(
                name = "Cartão",
                type = AccountType.CREDIT_CARD,
                creditLimitCents = 5_000_00,
                closingDay = 10,
                dueDay = 17,
            ),
        )

        assertNull(dao.byId(corrente)?.closingDay)
        assertEquals(10, dao.byId(cartao)?.closingDay)
        assertEquals(17, dao.byId(cartao)?.dueDay)
        assertEquals(5_000_00L, dao.byId(cartao)?.creditLimitCents)
    }

    @Test
    fun `arquivada sai da lista ativa sem sumir do banco`() = runBlocking {
        val id = dao.upsert(CONTA.copy(name = "Poupança antiga"))
        dao.upsert(dao.byId(id)!!.copy(archived = true))

        assertTrue(dao.observeActive().first().isEmpty())
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `apagar a conta de pagamento nao apaga o cartao`() = runBlocking {
        // SET_NULL, não CASCADE: perder o cartão inteiro porque a conta corrente
        // foi excluída levaria junto todo o histórico de fatura.
        val corrente = dao.upsert(CONTA.copy(name = "Corrente"))
        val cartao = dao.upsert(
            CONTA.copy(name = "Cartão", type = AccountType.CREDIT_CARD, paymentAccountId = corrente),
        )

        dao.delete(dao.byId(corrente)!!)

        assertEquals("Cartão", dao.byId(cartao)?.name)
        assertNull(dao.byId(cartao)?.paymentAccountId)
    }
}
