package app.financepro.domain

import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind

/**
 * Bases para variar com `copy` nos testes de domínio.
 *
 * `colorArgb` e `iconKey` são obrigatórios no modelo — REQ-ACC-001 e REQ-CAT-001
 * dizem que toda conta e toda categoria têm os dois. Num teste de saldo eles são
 * ruído, e o lugar do ruído é aqui, uma vez, e não em cada `assertEquals`.
 */
val UMA_CONTA = Account(
    id = 0,
    name = "Conta",
    type = AccountType.CHECKING,
    colorArgb = 0,
    iconKey = "wallet",
)

val UMA_CATEGORIA = Category(
    id = 0,
    name = "Categoria",
    kind = CategoryKind.EXPENSE,
    colorArgb = 0,
    iconKey = "tag",
)
