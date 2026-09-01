package app.financepro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Banco do app. REQ-DATA-001 · REQ-DATA-002 · REQ-DATA-003
 *
 * `exportSchema = true` grava `app/schemas/…/1.json`, versionado no
 * repositório: é o que torna cada `Migration` revisável em diff, e sem ele
 * `fallbackToDestructiveMigration()` — proibido pelo Art. 12 — viraria a saída
 * fácil no primeiro schema quebrado.
 *
 * Quem chama [buildDatabase] é o módulo Hilt (T-009), mas a construção mora
 * aqui: FK ligadas, semente e cifragem não podem ficar a critério de quem monta
 * o grafo de dependências.
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
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao

    abstract fun backupDao(): BackupDao

    abstract fun payeeRuleDao(): PayeeRuleDao

    abstract fun importBatchDao(): ImportBatchDao

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

/**
 * O banco de verdade, cifrado. REQ-SEC-001 · [ADR-010](../../../../../../../../docs/decisoes.md)
 *
 * `fallbackToDestructiveMigration` não aparece aqui e não vai aparecer (Art. 12,
 * REQ-DATA-001): apagar o histórico financeiro do usuário para vencer um erro de
 * migração não é uma opção, e `tools/trace.py` reprova o build se alguém tentar.
 *
 * Os testes de DAO **não** passam por esta função — eles montam o banco em
 * memória direto pelo Room. A biblioteca nativa do SQLCipher não existe na JVM,
 * e o que ela troca é o `openHelperFactory`, não uma linha de SQL. O preço é que
 * REQ-SEC-001 e REQ-SEC-002 só se verificam no aparelho, e a spec já os marca
 * como `Teste: manual`.
 */
fun buildDatabase(context: Context): AppDatabase {
    System.loadLibrary("sqlcipher")
    return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
        .openHelperFactory(SupportOpenHelperFactory(DatabaseKey(context).getOrCreate()))
        .addCallback(AppDatabase.ForeignKeysOn)
        .addCallback(SeedCallback)
        .build()
}
