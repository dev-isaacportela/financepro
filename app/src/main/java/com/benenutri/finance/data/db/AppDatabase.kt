package com.benenutri.finance.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Banco do app. REQ-DATA-001 · REQ-DATA-002 · REQ-DATA-003
 *
 * `exportSchema = true` grava `app/schemas/…/1.json`, versionado no
 * repositório: é o que torna cada `Migration` revisável em diff, e sem ele
 * `fallbackToDestructiveMigration()` — proibido pelo Art. 12 — viraria a saída
 * fácil no primeiro schema quebrado.
 *
 * Quem monta o `RoomDatabase.Builder` é o módulo Hilt (T-009), com o
 * `openHelperFactory` do SQLCipher (T-005). O que mora aqui é só o que não pode
 * ficar a critério de quem constrói: [ForeignKeysOn].
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TxnEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        ImportBatchEntity::class,
        PayeeRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao

    companion object {
        const val NAME = "finance.db"

        /**
         * REQ-DATA-002 — `PRAGMA foreign_keys = ON` em toda abertura.
         *
         * O SQLite vem com foreign keys **desligadas** por padrão, e o pragma
         * é por conexão, não por arquivo. Sem isto, `ON DELETE CASCADE` e
         * `ON DELETE RESTRICT` são aceitos na DDL e silenciosamente ignorados —
         * a proteção de REQ-CAT-005 existiria só no papel, e apagar uma
         * categoria deixaria transação órfã apontando para nada.
         */
        val ForeignKeysOn = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
