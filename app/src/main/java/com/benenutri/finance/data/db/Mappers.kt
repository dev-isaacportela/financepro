package com.benenutri.finance.data.db

import com.benenutri.finance.domain.model.Account
import com.benenutri.finance.domain.model.Category
import com.benenutri.finance.domain.model.Txn
import java.time.LocalDate

/**
 * Entidade → modelo de domínio.
 *
 * A tradução de data vive aqui, não num `TypeConverter`: a coluna guarda
 * `epochDay` justamente para o SQL comparar e ordenar data como inteiro, sem
 * função de data do SQLite. Um converter esconderia isso e faria toda `@Query`
 * de intervalo parecer mágica.
 *
 * Só o sentido entidade → domínio existe. A volta é do repositório (T-009), que
 * é quem tem `colorArgb`, `iconKey` e `sortOrder` vindos da tela — campos de
 * apresentação que o domínio não precisa conhecer para calcular saldo.
 */

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = type,
    initialBalanceCents = initialBalanceCents,
    archived = archived,
    creditLimitCents = creditLimitCents,
    closingDay = closingDay,
    dueDay = dueDay,
    paymentAccountId = paymentAccountId,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    kind = kind,
    parentId = parentId,
    archived = archived,
)

fun TxnEntity.toDomain() = Txn(
    id = id,
    accountId = accountId,
    type = type,
    amountCents = amountCents,
    date = LocalDate.ofEpochDay(date),
    counterAccountId = counterAccountId,
    categoryId = categoryId,
    description = description,
    cleared = cleared,
    installmentGroupId = installmentGroupId,
    installmentIndex = installmentIndex,
    installmentTotal = installmentTotal,
)
