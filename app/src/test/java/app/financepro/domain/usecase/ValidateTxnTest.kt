package app.financepro.domain.usecase

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CATEGORIA
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-CORE-002 · REQ-ACC-006 · REQ-CAT-003 · REQ-TXN-004 · REQ-TXN-005 ·
 * REQ-TXN-013 — cada linha da §5 da spec, com a mensagem exata.
 */
@Req("REQ-CORE-002", "REQ-ACC-006", "REQ-CAT-003", "REQ-TXN-004", "REQ-TXN-005", "REQ-TXN-013")
class ValidateTxnTest {

    private val hoje = LocalDate.of(2026, 8, 31)

    private val contas = listOf(
        UMA_CONTA.copy(id = 1, name = "Corrente"),
        UMA_CONTA.copy(id = 2, name = "Carteira", type = AccountType.CASH),
        UMA_CONTA.copy(id = 3, name = "Antiga", type = AccountType.SAVINGS, archived = true),
    ).associateBy { it.id }

    // Cor e ícone são obrigatórios de propósito: sem valor padrão, ninguém cria
    // categoria sem sticker por esquecimento. Aqui eles são só ruído.
    private val categorias = listOf(
        UMA_CATEGORIA.copy(id = 10, name = "Alimentação"),
        UMA_CATEGORIA.copy(id = 20, name = "Salário", kind = CategoryKind.INCOME),
    ).associateBy { it.id }

    private fun validar(t: Txn) = validateTxn(t, contas, categorias, hoje)
    private fun mensagens(t: Txn) = validar(t).map { it.mensagem }

    private val despesaValida = Txn(
        accountId = 1, type = TxnType.EXPENSE, amountCents = -1850,
        date = hoje, categoryId = 10,
    )

    @Test
    fun `transacao valida nao gera erro`() {
        assertTrue(validar(despesaValida).isEmpty())
    }

    @Test
    fun `valor zero`() {
        assertTrue("Informe um valor" in mensagens(despesaValida.copy(amountCents = 0)))
    }

    @Test
    fun `conta arquivada e somente leitura`() {
        assertTrue("Conta arquivada" in mensagens(despesaValida.copy(accountId = 3)))
    }

    @Test
    fun `transferencia exige destino diferente`() {
        val base = Txn(
            accountId = 1, type = TxnType.TRANSFER, amountCents = -5000, date = hoje,
        )
        val esperado = "Escolha uma conta de destino diferente"
        assertTrue(esperado in mensagens(base))                                  // sem destino
        assertTrue(esperado in mensagens(base.copy(counterAccountId = 1)))       // destino == origem
        assertTrue(validar(base.copy(counterAccountId = 2)).isEmpty())           // ok
    }

    @Test
    fun `transferencia para conta arquivada e recusada`() {
        val t = Txn(
            accountId = 1, counterAccountId = 3, type = TxnType.TRANSFER,
            amountCents = -5000, date = hoje,
        )
        assertTrue("Conta arquivada" in mensagens(t))
    }

    @Test
    fun `transferencia nao exige categoria`() {
        val t = Txn(
            accountId = 1, counterAccountId = 2, type = TxnType.TRANSFER,
            amountCents = -5000, date = hoje, categoryId = null,
        )
        assertTrue(validar(t).isEmpty())
        // E a categoria é removida antes de gravar, não exibida como erro.
        assertEquals(null, sanitize(t.copy(categoryId = 10)).categoryId)
    }

    @Test
    fun `despesa e receita exigem categoria`() {
        assertTrue(
            "Escolha uma categoria" in mensagens(despesaValida.copy(categoryId = null)),
        )
    }

    @Test
    fun `natureza da categoria tem de bater com o tipo`() {
        // Categoria de Salário (INCOME) numa despesa.
        assertTrue(
            "Categoria de receita em uma despesa"
                in mensagens(despesaValida.copy(categoryId = 20)),
        )
        // E o inverso: categoria de despesa numa receita.
        val receita = Txn(
            accountId = 1, type = TxnType.INCOME, amountCents = 450000,
            date = hoje, categoryId = 10,
        )
        assertTrue("Categoria de receita em uma despesa" in mensagens(receita))
    }

    @Test
    fun `data muito distante`() {
        val limite = hoje.plusYears(5)
        assertTrue(validar(despesaValida.copy(date = limite)).isEmpty())
        assertTrue(
            "Data muito distante" in mensagens(despesaValida.copy(date = limite.plusDays(1))),
        )
    }

    @Test
    fun `data passada e sempre valida`() {
        // Lançamento retroativo é caso normal: o usuário registra o que gastou
        // ontem, ou importa extrato de meses atrás.
        assertTrue(validar(despesaValida.copy(date = hoje.minusYears(3))).isEmpty())
    }

    @Test
    fun `devolve todos os erros de uma vez`() {
        // A UI mostra tudo junto; o usuário corrige numa passada em vez de
        // descobrir um problema por vez.
        val ruim = Txn(
            accountId = 3,                       // arquivada
            type = TxnType.EXPENSE,
            amountCents = 0,                     // zero
            date = hoje.plusYears(9),            // longe demais
            categoryId = null,                   // sem categoria
        )
        val msgs = mensagens(ruim)
        assertEquals(4, msgs.size)
        listOf(
            "Informe um valor", "Conta arquivada",
            "Escolha uma categoria", "Data muito distante",
        ).forEach { assertTrue("faltou: $it", it in msgs) }
    }
}
