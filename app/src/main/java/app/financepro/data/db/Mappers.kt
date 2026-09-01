package app.financepro.data.db

import app.financepro.domain.model.Account
import app.financepro.domain.model.Budget
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import java.time.LocalDate
import java.time.YearMonth

/**
 * Entidade → modelo de domínio.
 *
 * A tradução de data vive aqui, não num `TypeConverter`: a coluna guarda
 * `epochDay` justamente para o SQL comparar e ordenar data como inteiro, sem
 * função de data do SQLite. Um converter esconderia isso e faria toda `@Query`
 * de intervalo parecer mágica.
 *
 * Só o sentido entidade → domínio existe. A volta é do repositório, na tela que
 * escreve: é ela que sabe se está criando uma conta, editando uma categoria ou
 * gravando uma transação, e cada uma preenche colunas diferentes.
 */

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    type = type,
    colorArgb = colorArgb,
    iconKey = iconKey,
    initialBalanceCents = initialBalanceCents,
    archived = archived,
    sortOrder = sortOrder,
    creditLimitCents = creditLimitCents,
    closingDay = closingDay,
    dueDay = dueDay,
    paymentAccountId = paymentAccountId,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    kind = kind,
    colorArgb = colorArgb,
    iconKey = iconKey,
    parentId = parentId,
    archived = archived,
)

/**
 * `yyyyMM`, ex. 202608. REQ-BUD-001
 *
 * Inteiro na coluna porque ordena e compara em SQL sem função de data, pela
 * mesma razão de `date` ser `epochDay` — e a conversão fica **aqui**, na borda,
 * uma vez para cada direção.
 */
fun YearMonth.toYearMonthInt(): Int = year * 100 + monthValue

fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 100, this % 100)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryId = categoryId,
    month = yearMonth.toYearMonth(),
    limitCents = limitCents,
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
