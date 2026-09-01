package app.financepro.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAOs. Um por agregado, e só o que já tem chamador.
 *
 * Leitura sai como `Flow` (arquitetura.md §3, regra 4): a UI observa, nunca
 * busca imperativamente, e uma transação nova reflete na lista, no saldo e no
 * dashboard sem ninguém orquestrar recarga.
 *
 * Nenhuma aritmética de dinheiro em `@Query` (§3, regra 2). Saldo, fatura,
 * parcela e orçamento moram em `domain/usecase` como função pura — é o que
 * torna cada uma testável em JVM sem banco. Aqui o SQL só filtra e ordena.
 *
 * DAOs de `budget`, `recurring_rule`, `import_batch` e `payee_rule` entram nas
 * tasks que os usam (T-028, T-031, T-041). As entidades já existem porque `txn`
 * aponta para elas, mas DAO sem chamador é código morto nascendo.
 */

/** REQ-ACC-001 · REQ-ACC-002 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM account WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account ORDER BY archived, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Delete
    suspend fun delete(account: AccountEntity)
}

/**
 * REQ-CAT-001 · REQ-CAT-002 · REQ-CAT-005 · REQ-CAT-006
 *
 * Classe abstrata, não interface, por causa de [upsertChecked]: a hierarquia de
 * um nível não é expressável em DDL, e a checagem precisa acontecer na mesma
 * transação da escrita.
 */
@Dao
abstract class CategoryDao {

    @Query("SELECT * FROM category WHERE archived = 0 ORDER BY parentId IS NOT NULL, name")
    abstract fun observeActive(): Flow<List<CategoryEntity>>

    /** Ordem do grid do lançamento rápido: mais usadas primeiro (REQ-CAT-006). */
    @Query(
        """
        SELECT * FROM category
        WHERE archived = 0 AND kind = :kind
        ORDER BY useCount DESC, name
        """,
    )
    abstract fun observeByUse(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    abstract suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE parentId = :parentId")
    abstract suspend fun childrenOf(parentId: Long): List<CategoryEntity>

    /**
     * Quantas transações seguram esta categoria. REQ-CAT-005
     *
     * A exclusão é recusada pelo banco de qualquer forma; isto existe para a
     * mensagem dizer **quantas** — "Mova as 37 transações antes" é acionável,
     * "não é possível excluir" não é.
     */
    @Query("SELECT COUNT(*) FROM txn WHERE categoryId = :id")
    abstract suspend fun contarTransacoes(id: Long): Int

    @Query("UPDATE category SET useCount = useCount + 1 WHERE id = :id")
    abstract suspend fun bumpUse(id: Long)

    @Upsert
    abstract suspend fun upsert(category: CategoryEntity): Long

    /**
     * Excluir categoria com transação falha aqui, no banco, por `RESTRICT`
     * (REQ-CAT-005). A UI oferece recategorização em lote (T-016); o que ela
     * **não** faz é decidir sozinha se pode apagar.
     */
    @Delete
    abstract suspend fun delete(category: CategoryEntity)

    /**
     * REQ-CAT-002 — hierarquia de **um** nível.
     *
     * `parentId` só pode apontar para categoria raiz. O SQLite não expressa
     * isso: a FK garante que o pai existe, não que ele seja raiz. Fica na
     * mesma transação da escrita porque, fora dela, entre a checagem e o
     * `INSERT` o pai pode ter virado filho.
     */
    @Transaction
    open suspend fun upsertChecked(category: CategoryEntity): Long {
        val parent = category.parentId?.let { byId(it) }
        require(parent == null || parent.parentId == null) {
            "Subcategoria não pode ter subcategoria (REQ-CAT-002)"
        }
        return upsert(category)
    }
}

/** REQ-TXN-001 · REQ-TXN-010 */
@Dao
interface BudgetDao {

    /**
     * Todos os tetos, de todos os meses. REQ-BUD-001
     *
     * A tela recorta o mês em memória, como a lista de transações faz: são
     * poucas linhas — uma por categoria com teto, por mês — e um `Flow` por mês
     * exigiria refazer a assinatura a cada troca de mês, com `flatMapLatest`, e
     * um estado de carregamento por cima.
     */
    @Query("SELECT * FROM budget")
    fun observeAll(): Flow<List<BudgetEntity>>

    /**
     * O teto de um par (categoria, mês), quando existe. REQ-BUD-001
     *
     * É o que torna "no máximo um por par" verdade na escrita. `@Upsert` sozinho
     * não bastaria: em violação do índice único ele cai para um `UPDATE` pela
     * chave primária, que numa linha nova (`id = 0`) não casa com nada — a mesma
     * armadilha documentada em `TxnDao.insert`. Quem grava lê antes e reusa o id.
     */
    @Query("SELECT * FROM budget WHERE categoryId = :categoryId AND yearMonth = :yearMonth")
    suspend fun byCategoryAndMonth(categoryId: Long, yearMonth: Int): BudgetEntity?

    /** Os tetos de um mês. Base de REQ-BUD-005. */
    @Query("SELECT * FROM budget WHERE yearMonth = :yearMonth")
    suspend fun doMes(yearMonth: Int): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity): Long

    @Upsert
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Delete
    suspend fun delete(budget: BudgetEntity)
}

@Dao
interface TxnDao {

    /** `date` é epochDay: o intervalo é comparação de inteiro, sem função de data. */
    @Query("SELECT * FROM txn WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TxnEntity>>

    /**
     * Inclui a perna de destino da transferência: sem `counterAccountId` o
     * extrato da conta que recebe ficaria sem a linha (ADR-003).
     */
    @Query(
        """
        SELECT * FROM txn
        WHERE (accountId = :accountId OR counterAccountId = :accountId)
          AND date BETWEEN :from AND :to
        ORDER BY date DESC, id DESC
        """,
    )
    fun observeByAccount(accountId: Long, from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query("SELECT * FROM txn WHERE installmentGroupId = :groupId ORDER BY installmentIndex")
    suspend fun installmentGroup(groupId: String): List<TxnEntity>

    /**
     * Inserção que **falha** em conflito, e é assim que a importação escreve.
     *
     * `@Upsert` não serve para o caminho de importação: em violação de índice
     * único ele cai para um `UPDATE` pela chave primária, que numa linha nova
     * (`id = 0`) não casa com nada e vira silêncio. A duplicata sumiria sem
     * erro, e a rede de segurança do `dedupeKey` (ingestao.md §3) só existiria
     * no papel.
     */
    @Insert
    suspend fun insert(txn: TxnEntity): Long

    /** Repõe um grupo de parcelas de uma vez. Ver `TxnRepository.desfazerExclusao`. */
    @Insert
    suspend fun insertAll(txns: List<TxnEntity>)

    @Upsert
    suspend fun upsert(txn: TxnEntity): Long

    @Upsert
    suspend fun upsertAll(txns: List<TxnEntity>)

    @Delete
    suspend fun delete(txn: TxnEntity)

    /**
     * Exclui um grupo inteiro numa escrita só. REQ-TXN-009
     *
     * Meia compra parcelada excluída é dinheiro inventado no extrato, do mesmo
     * jeito que meia compra parcelada criada — é a razão de `upsertAll` existir
     * ao lado de `upsert`.
     */
    @Delete
    suspend fun deleteAll(txns: List<TxnEntity>)

    /**
     * Recategorização em lote. REQ-CAT-005
     *
     * É o que a UI oferece quando a exclusão é recusada. Uma escrita só: mover
     * transação por transação deixaria metade num limbo se o app morresse no
     * meio, e a categoria antiga continuaria inexcluível por um resto.
     */
    @Query("UPDATE txn SET categoryId = :destino WHERE categoryId = :origem")
    suspend fun recategorizar(origem: Long, destino: Long): Int
}
