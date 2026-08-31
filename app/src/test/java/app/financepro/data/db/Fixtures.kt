package app.financepro.data.db

import app.financepro.domain.model.AccountType
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.TxnType
import java.time.LocalDate

/**
 * Linhas-base para os testes de DAO, variadas por `copy`.
 *
 * `data class` já traz o builder pronto: `CONTA.copy(type = SAVINGS)` diz o que
 * o teste está exercitando e cala sobre cor, ícone e carimbo de tempo. Uma
 * função de fábrica com dez parâmetros default faria o mesmo, com dez linhas a
 * mais para manter em sincronia com a entidade.
 */

val CONTA = AccountEntity(
    name = "Conta",
    type = AccountType.CHECKING,
    colorArgb = 0xFF101010.toInt(),
    iconKey = "wallet",
)

val CATEGORIA = CategoryEntity(
    name = "Categoria",
    kind = CategoryKind.EXPENSE,
    iconKey = "tag",
    colorArgb = 0xFF202020.toInt(),
)

/** `accountId` sai zerado de propósito: sem conta real, o `INSERT` falha na FK. */
val LANCAMENTO = TxnEntity(
    accountId = 0,
    type = TxnType.EXPENSE,
    amountCents = -1_000,
    date = LocalDate.of(2026, 3, 10).toEpochDay(),
    createdAt = 0,
    updatedAt = 0,
)

fun dia(ano: Int, mes: Int, dia: Int): Long = LocalDate.of(ano, mes, dia).toEpochDay()
