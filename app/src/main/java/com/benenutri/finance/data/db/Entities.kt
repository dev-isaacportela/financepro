package com.benenutri.finance.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.benenutri.finance.domain.model.AccountType
import com.benenutri.finance.domain.model.CategoryKind
import com.benenutri.finance.domain.model.TxnType

/**
 * Entidades do Room. Espelham [arquitetura.md](../../../../../../../../docs/arquitetura.md) §4.
 *
 * Vivem separadas dos modelos de `domain/model/` de propósito (§3): o domínio é
 * Kotlin puro e testável em JVM em milissegundos, e é o que permite validar
 * saldo, fatura e orçamento sem emulador. Colar `@Entity` nos modelos economiza
 * este arquivo hoje e cobra o preço em toda regra de negócio depois.
 *
 * Os enums são gravados como TEXT pelo suporte nativo do Room — o valor no banco
 * é o `name` da constante, legível em qualquer inspetor de SQLite. Ordinal seria
 * menor e quebraria no dia em que alguém reordenasse o enum.
 */

/** REQ-ACC-001 · REQ-ACC-002 · REQ-CARD-001 */
@Entity(
    tableName = "account",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentAccountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("paymentAccountId")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val initialBalanceCents: Long = 0,
    val colorArgb: Int,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    // Só CREDIT_CARD. Nuláveis porque conta corrente não tem fatura; a
    // obrigatoriedade para cartão é de REQ-ACC-002, validada no domínio.
    val creditLimitCents: Long? = null,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
    val paymentAccountId: Long? = null,
)

/** REQ-CAT-001 · REQ-CAT-002 */
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val iconKey: String,
    val colorArgb: Int,
    val archived: Boolean = false,
    /** Ordena o grid do lançamento rápido (REQ-CAT-006). */
    val useCount: Int = 0,
)

/**
 * REQ-TXN-001 · REQ-TXN-002 · REQ-TXN-003
 *
 * `categoryId` é `RESTRICT`: é assim que REQ-CAT-005 ("não dá para excluir
 * categoria com transações") vira uma garantia do banco, em vez de uma checagem
 * que alguém esquece de chamar num caminho novo.
 *
 * O índice único em `(accountId, dedupeKey)` é a rede de segurança embaixo do
 * dedupe da F2 (ingestao.md §3). A §4.1 o descreve com `WHERE dedupeKey IS NOT
 * NULL`; aqui ele é total, porque no SQLite dois `NULL` **nunca** colidem num
 * índice único — o efeito é idêntico e cabe no `@Index` do Room, sem SQL solto
 * fora do schema exportado.
 */
@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId", "date"),
        Index("counterAccountId"),
        Index("date"),
        Index("categoryId", "date"),
        Index("installmentGroupId"),
        Index("cleared", "date"),
        Index("recurringRuleId"),
        Index("importBatchId"),
        Index(value = ["accountId", "dedupeKey"], unique = true),
    ],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val counterAccountId: Long? = null,
    val categoryId: Long? = null,
    val type: TxnType,
    /** Efeito líquido em [accountId], em centavos. Nunca zero (REQ-TXN-013). */
    val amountCents: Long,
    /** `LocalDate.toEpochDay()`. Inteiro ordena e compara em SQL sem converter. */
    val date: Long,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    val notes: String? = null,
    val cleared: Boolean = true,
    val installmentGroupId: String? = null,
    val installmentIndex: Int? = null,
    val installmentTotal: Int? = null,
    val recurringRuleId: Long? = null,
    val importBatchId: Long? = null,
    val dedupeKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** REQ-BUD-001 */
@Entity(
    tableName = "budget",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId", "yearMonth"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** `yyyyMM`, ex. `202608`. */
    val yearMonth: Int,
    val limitCents: Long,
)

/** REQ-REC-001 */
@Entity(
    tableName = "recurring_rule",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("counterAccountId"), Index("categoryId")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val counterAccountId: Long? = null,
    val categoryId: Long? = null,
    val type: TxnType,
    val amountCents: Long,
    val description: String,
    val freq: String,
    @ColumnInfo(defaultValue = "1") val interval: Int = 1,
    val dayOfMonth: Int? = null,
    val weekday: Int? = null,
    val monthOfYear: Int? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val lastGeneratedDate: Long? = null,
    val autoPost: Boolean = false,
    val active: Boolean = true,
)

/** REQ-IMP-011 */
@Entity(
    tableName = "import_batch",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val sourceType: String,
    val sourceName: String,
    val importedAt: Long,
    val txnCount: Int,
)

/** REQ-ACT-001 · REQ-ACT-003 — regras de auto-categorização (ingestao.md §4). */
@Entity(
    tableName = "payee_rule",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["normalizedKey"], unique = true), Index("categoryId")],
)
data class PayeeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedKey: String,
    val categoryId: Long,
    @ColumnInfo(defaultValue = "1") val hitCount: Int = 1,
)
