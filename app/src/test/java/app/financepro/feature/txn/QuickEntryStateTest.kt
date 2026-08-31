package app.financepro.feature.txn

import app.financepro.core.testing.Req
import app.financepro.domain.UMA_CATEGORIA
import app.financepro.domain.UMA_CONTA
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-UI-003 — os campos condicionais.
 *
 * A regra mora em `QuickEntryState` como propriedade derivada, e não espalhada
 * em `if` pela folha, justamente para caber num teste de JVM: aparecer e sumir
 * campo é lógica, e lógica que só existe dentro de `@Composable` só se verifica
 * com emulador.
 */
@Req("REQ-UI-003")
class QuickEntryStateTest {

    private val corrente = UMA_CONTA.copy(id = 1, name = "Corrente")
    private val cartao =
        UMA_CONTA.copy(id = 2, name = "Cartão", type = AccountType.CREDIT_CARD, closingDay = 10, dueDay = 17)
    private val base = QuickEntryState(contas = listOf(corrente, cartao), contaId = corrente.id)

    @Test
    fun `parcelas so aparece em cartao de credito`() {
        assertFalse(base.mostraParcelas)
        assertTrue(base.copy(contaId = cartao.id).mostraParcelas)
    }

    @Test
    fun `parcelas some quando o cartao recebe receita ou transferencia`() {
        // Parcelar entrada de dinheiro não existe, e transferência para o cartão
        // é pagamento de fatura — nenhum dos dois se divide em N vezes.
        val noCartao = base.copy(contaId = cartao.id)

        assertFalse(noCartao.copy(tipo = TxnType.INCOME).mostraParcelas)
        assertFalse(noCartao.copy(tipo = TxnType.TRANSFER).mostraParcelas)
    }

    @Test
    fun `destino so aparece em transferencia, e categoria some nela`() {
        assertFalse(base.mostraDestino)
        assertTrue(base.mostraCategoria)

        val transferencia = base.copy(tipo = TxnType.TRANSFER)
        assertTrue(transferencia.mostraDestino)
        assertFalse(transferencia.mostraCategoria)
    }

    @Test
    fun `o grid muda de conjunto junto com o tipo`() {
        // REQ-CAT-003: o mesmo estado não pode oferecer "Salário" numa despesa.
        val comCategorias = base.copy(
            porTipo = mapOf(
                CategoryKind.EXPENSE to listOf(gasto),
                CategoryKind.INCOME to listOf(receita),
            ),
        )

        assertEquals(listOf("Mercado"), comCategorias.categorias.map { it.name })
        assertEquals(
            listOf("Salário"),
            comCategorias.copy(tipo = TxnType.INCOME).categorias.map { it.name },
        )
    }

    private companion object {
        val gasto = UMA_CATEGORIA.copy(id = 1, name = "Mercado")
        val receita = UMA_CATEGORIA.copy(id = 2, name = "Salário", kind = CategoryKind.INCOME)
    }
}
