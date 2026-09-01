package app.financepro.data.db

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * O grafo do banco.
 *
 * Mora em `data/db/` e não num pacote `di/` porque é aqui que estão as coisas
 * que ele provê. Um pacote só de módulos Hilt vira um índice remoto do resto do
 * app, que alguém precisa manter em sincronia sem ganhar nada.
 *
 * Os repositórios não aparecem: eles têm `@Inject constructor`, e o Hilt os
 * constrói sozinho. Um `@Provides` por repositório seria uma lista a mais para
 * esquecer de atualizar.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = buildDatabase(context)

    @Provides
    fun accountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun categoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun txnDao(db: AppDatabase): TxnDao = db.txnDao()

    @Provides
    fun budgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun recurringDao(db: AppDatabase): RecurringDao = db.recurringDao()

    @Provides
    fun backupDao(db: AppDatabase): BackupDao = db.backupDao()

    @Provides
    fun payeeRuleDao(db: AppDatabase): PayeeRuleDao = db.payeeRuleDao()

    @Provides
    fun importBatchDao(db: AppDatabase): ImportBatchDao = db.importBatchDao()
}
