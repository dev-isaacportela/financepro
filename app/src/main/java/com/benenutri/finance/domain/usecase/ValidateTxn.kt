package com.benenutri.finance.domain.usecase

import com.benenutri.finance.domain.model.Account
import com.benenutri.finance.domain.model.Category
import com.benenutri.finance.domain.model.CategoryKind
import com.benenutri.finance.domain.model.Txn
import com.benenutri.finance.domain.model.TxnType
import java.time.LocalDate

/**
 * Validação de transação. §5 da spec.
 *
 * REQ-CORE-002 · REQ-ACC-006 · REQ-CAT-003 · REQ-TXN-004 · REQ-TXN-005 ·
 * REQ-TXN-013 · Art. 9
 *
 * Devolve **todos** os erros de uma vez, em vez de lançar no primeiro: a UI
 * mostra tudo junto e o usuário corrige numa passada, em vez de descobrir um
 * problema por vez.
 *
 * Vive no domínio, não na UI e não em `@Query` — duas fontes de verdade para a
 * mesma regra divergem, sempre.
 */

/** Horizonte máximo de data futura. REQ-TXN-013. */
private const val MAX_ANOS_FUTURO = 5L

/** Erro de validação, com a mensagem exata que a UI exibe. */
data class ValidationError(val campo: Campo, val mensagem: String) {
    enum class Campo { VALOR, CONTA, CONTA_DESTINO, CATEGORIA, DATA }
}

/**
 * Valida [txn] no contexto das contas e categorias existentes.
 *
 * [hoje] é injetado em vez de lido de `LocalDate.now()` para que a regra seja
 * determinística no teste — data do sistema dentro de função pura torna o teste
 * dependente do dia em que roda.
 */
fun validateTxn(
    txn: Txn,
    contas: Map<Long, Account>,
    categorias: Map<Long, Category>,
    hoje: LocalDate,
): List<ValidationError> {
    val erros = mutableListOf<ValidationError>()
    fun erro(campo: ValidationError.Campo, msg: String) =
        erros.add(ValidationError(campo, msg))

    // REQ-CORE-002
    if (txn.amountCents == 0L) {
        erro(ValidationError.Campo.VALOR, "Informe um valor")
    }

    // REQ-ACC-006 — conta arquivada é somente leitura.
    val origem = contas[txn.accountId]
    when {
        origem == null -> erro(ValidationError.Campo.CONTA, "Conta não encontrada")
        origem.archived -> erro(ValidationError.Campo.CONTA, "Conta arquivada")
    }

    when (txn.type) {
        // REQ-TXN-003 e REQ-TXN-004
        TxnType.TRANSFER -> {
            val destino = txn.counterAccountId
            when {
                destino == null || destino == txn.accountId ->
                    erro(
                        ValidationError.Campo.CONTA_DESTINO,
                        "Escolha uma conta de destino diferente",
                    )
                contas[destino] == null ->
                    erro(ValidationError.Campo.CONTA_DESTINO, "Conta não encontrada")
                contas.getValue(destino).archived ->
                    erro(ValidationError.Campo.CONTA_DESTINO, "Conta arquivada")
            }
            // categoryId nulo em transferência é sanitizado, não exibido:
            // transferência não é receita nem despesa, e contá-la como tal
            // duplicaria o valor nos relatórios.
        }

        // REQ-TXN-005 e REQ-CAT-003
        TxnType.INCOME, TxnType.EXPENSE -> {
            val categoria = txn.categoryId?.let { categorias[it] }
            val esperado =
                if (txn.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            when {
                txn.categoryId == null ->
                    erro(ValidationError.Campo.CATEGORIA, "Escolha uma categoria")
                categoria == null ->
                    erro(ValidationError.Campo.CATEGORIA, "Categoria não encontrada")
                categoria.kind != esperado ->
                    erro(
                        ValidationError.Campo.CATEGORIA,
                        "Categoria de receita em uma despesa",
                    )
            }
        }
    }

    // REQ-TXN-013
    if (txn.date.isAfter(hoje.plusYears(MAX_ANOS_FUTURO))) {
        erro(ValidationError.Campo.DATA, "Data muito distante")
    }

    return erros
}

/** Remove o que não faz sentido para o tipo, antes de gravar. REQ-TXN-004. */
fun sanitize(txn: Txn): Txn = when (txn.type) {
    TxnType.TRANSFER -> txn.copy(categoryId = null)
    TxnType.INCOME, TxnType.EXPENSE -> txn.copy(counterAccountId = null)
}
