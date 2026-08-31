package com.benenutri.finance.domain.usecase

import com.benenutri.finance.core.testing.Req
import com.benenutri.finance.domain.model.Account
import com.benenutri.finance.domain.model.AccountType
import com.benenutri.finance.domain.model.Txn
import com.benenutri.finance.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-TXN-003 — transferência é UM registro, não dois.
 *
 * Duas linhas espelhadas exigiriam sincronia em editar, excluir, desfazer lote
 * e migrar. Cada uma dessas é uma chance de deixar meia transferência órfã — e
 * meia transferência é dinheiro inventado ou sumido no saldo do usuário.
 */
@Req("REQ-TXN-003")
class TransferTest {

    private val hoje = LocalDate.of(2026, 8, 31)
    private val a = Account(1, "A", AccountType.CHECKING, initialBalanceCents = 20000)
    private val b = Account(2, "B", AccountType.CASH)

    private val transferencia = Txn(
        accountId = 1, counterAccountId = 2, type = TxnType.TRANSFER,
        amountCents = -5000, date = hoje,
    )

    @Test
    fun `um unico registro afeta as duas contas`() {
        val txns = listOf(transferencia)
        assertEquals(1, txns.size)
        assertEquals(15000L, balanceOf(a, txns))
        assertEquals(5000L, balanceOf(b, txns))
    }

    @Test
    fun `o valor e o efeito na conta de origem, e negativo`() {
        assertEquals(-5000L, transferencia.amountCents)
    }

    @Test
    fun `transferencia nao tem categoria`() {
        // Não é receita nem despesa. Contá-la como tal duplicaria o valor nos
        // relatórios (REQ-TXN-004).
        assertNull(sanitize(transferencia.copy(categoryId = 9)).categoryId)
    }

    @Test
    fun `excluir a linha desfaz a transferencia inteira`() {
        // Com duas linhas, excluir uma deixaria a outra órfã. Com uma, some
        // dos dois lados de uma vez.
        assertEquals(20000L, balanceOf(a, emptyList()))
        assertEquals(0L, balanceOf(b, emptyList()))
    }
}
