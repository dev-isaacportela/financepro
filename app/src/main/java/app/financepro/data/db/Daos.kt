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
 * DAOs de `import_batch` e `payee_rule` entram na task que os usa (T-041). As
 * entidades já existem porque `txn` aponta para elas, mas DAO sem chamador é
 * código morto nascendo.
 */

/** REQ-ACC-001 · REQ-ACC-002 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM account WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account ORDER BY archived, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    /** Tudo, para o backup. REQ-BAK-001 */
    @Query("SELECT * FROM account")
    suspend fun todas(): List<AccountEntity>

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

    /** Tudo, inclusive arquivada: backup sem a categoria perde a transação dela. */
    @Query("SELECT * FROM category")
    abstract suspend fun todas(): List<CategoryEntity>

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
    /** Tudo, para o backup. REQ-BAK-001 */
    @Query("SELECT * FROM budget")
    suspend fun todas(): List<BudgetEntity>

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

    /** Tudo, para o backup. REQ-BAK-001 */
    @Query("SELECT * FROM txn")
    suspend fun todas(): List<TxnEntity>

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

/**
 * REQ-REC-001 · REQ-REC-003 · REQ-REC-007
 *
 * Classe abstrata, não interface, pelas duas transações: materializar e
 * reescrever precisam ser atômicas, e uma transação que atravessa dois métodos
 * chamados de fora não é transação.
 *
 * Escreve em `txn` apesar de ser o DAO da regra. É deliberado: as linhas
 * geradas e o `lastGeneratedDate` que as registra têm de entrar juntos, e um
 * `TxnDao` injetado ao lado seriam duas conexões e duas transações.
 *
 * Excluir a regra **não** leva as ocorrências junto: `txn.recurringRuleId` é
 * `SET_NULL`, e o histórico do aluguel pago continua no extrato depois de o
 * aluguel deixar de ser recorrente.
 */
@Dao
abstract class RecurringDao {

    @Query("SELECT * FROM recurring_rule ORDER BY active DESC, description")
    abstract fun observeAll(): Flow<List<RecurringRuleEntity>>

    /** As candidatas à geração. Regra inativa não materializa nada (REQ-REC-001). */
    /** Tudo, para o backup. REQ-BAK-001 */
    @Query("SELECT * FROM recurring_rule")
    abstract suspend fun todas(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rule WHERE active = 1")
    abstract suspend fun ativas(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rule WHERE id = :id")
    abstract suspend fun byId(id: Long): RecurringRuleEntity?

    @Upsert
    abstract suspend fun upsert(rule: RecurringRuleEntity): Long

    @Delete
    abstract suspend fun delete(rule: RecurringRuleEntity)

    @Query("SELECT MAX(date) FROM txn WHERE recurringRuleId = :ruleId")
    abstract suspend fun ultimaOcorrencia(ruleId: Long): Long?

    @Query("UPDATE recurring_rule SET lastGeneratedDate = :ate WHERE id = :ruleId")
    abstract suspend fun marcarGerado(ruleId: Long, ate: Long?)

    @Query(
        """
        DELETE FROM txn
        WHERE recurringRuleId = :ruleId AND cleared = 0 AND date >= :apartirDe
        """,
    )
    abstract suspend fun apagarFuturasNaoEfetivadas(ruleId: Long, apartirDe: Long): Int

    @Insert
    abstract suspend fun inserirOcorrencias(txns: List<TxnEntity>)

    /**
     * As ocorrências novas e a marca de até onde se foi, numa escrita só.
     * REQ-REC-003
     *
     * Separar as duas abriria a janela que quebra a idempotência: linhas
     * gravadas com o `lastGeneratedDate` antigo — porque o processo morreu no
     * meio — são regeradas na abertura seguinte, e aí o aluguel aparece duas
     * vezes. É exatamente o defeito que o requisito existe para fechar.
     */
    @Transaction
    open suspend fun materializar(ruleId: Long, txns: List<TxnEntity>, ate: Long) {
        inserirOcorrencias(txns)
        marcarGerado(ruleId, ate)
    }

    /**
     * Grava a regra e, quando é alteração, descarta as ocorrências futuras
     * ainda não efetivadas. REQ-REC-001 · REQ-REC-007 · Devolve o id.
     *
     * **O id não vem do `@Upsert`.** Ele devolve `-1` quando o caminho tomado
     * foi `UPDATE`, e usar esse retorno faria a limpeza abaixo rodar contra a
     * regra `-1` — que não existe: nada seria apagado, e a alteração pareceria
     * ter funcionado enquanto as ocorrências antigas continuavam lá.
     *
     * `cleared = 0 AND date >= hoje` é o requisito literal: o que já foi
     * efetivado é histórico e não muda, e o que já passou sem ser efetivado
     * também fica — apagá-lo seria o app decidir que uma conta vencida e não
     * paga nunca existiu.
     *
     * A marca volta para a **última ocorrência que sobrou**, e não para nulo:
     * nulo faria a geração seguinte recomeçar do `startDate` e duplicar todo o
     * histórico. Ela é recalculada aqui dentro justamente porque o `UPDATE`
     * acabou de gravar o `lastGeneratedDate` que veio na regra, que é o valor
     * que a tela leu antes de editar.
     */
    @Transaction
    open suspend fun salvar(rule: RecurringRuleEntity, hoje: Long): Long {
        val gerado = upsert(rule)
        if (rule.id == 0L) return gerado
        apagarFuturasNaoEfetivadas(rule.id, hoje)
        marcarGerado(rule.id, ultimaOcorrencia(rule.id))
        return rule.id
    }
}

/**
 * A troca de base inteira. REQ-BAK-003 · REQ-BAK-004
 *
 * Classe abstrata, e não interface, por causa de [substituir]: apagar e repor
 * precisam acontecer na **mesma** transação. Separados, um processo morto no
 * meio deixaria o usuário sem base nenhuma — que é o oposto do que restaurar um
 * backup deveria fazer.
 *
 * A ordem não é estética. Apagar vai do filho para o pai e inserir vai do pai
 * para o filho, porque as FKs estão ligadas (REQ-DATA-002) e `RESTRICT` em
 * `txn.categoryId` recusaria apagar categoria antes das transações dela.
 * `clearAllTables()` do Room faria o apagar sozinho, e desligando as FKs para
 * isso — o que também apagaria as duas tabelas da F2 que o backup ainda não
 * carrega, e restaurar levaria embora o que ele não sabe repor.
 */
@Dao
abstract class BackupDao {

    @Query("DELETE FROM txn")
    abstract suspend fun apagarTxns()

    @Query("DELETE FROM budget")
    abstract suspend fun apagarTetos()

    @Query("DELETE FROM recurring_rule")
    abstract suspend fun apagarRegras()

    @Query("DELETE FROM category")
    abstract suspend fun apagarCategorias()

    @Query("DELETE FROM account")
    abstract suspend fun apagarContas()

    @Insert
    abstract suspend fun inserirContas(linhas: List<AccountEntity>)

    @Insert
    abstract suspend fun inserirCategorias(linhas: List<CategoryEntity>)

    @Insert
    abstract suspend fun inserirRegras(linhas: List<RecurringRuleEntity>)

    @Insert
    abstract suspend fun inserirTetos(linhas: List<BudgetEntity>)

    @Insert
    abstract suspend fun inserirTxns(linhas: List<TxnEntity>)

    /**
     * Do filho para o pai, senão a FK recusa.
     *
     * `payee_rule` não aparece: `ON DELETE CASCADE` em `categoryId` já a leva
     * junto das categorias. Uma linha a mais aqui seria SQL que não faz nada.
     */
    @Transaction
    open suspend fun limpar() {
        apagarTxns()
        apagarTetos()
        apagarRegras()
        apagarCategorias()
        apagarContas()
    }

    /**
     * REQ-BAK-004 — apagar tudo devolve o app ao estado de instalação, não a um
     * banco vazio.
     *
     * A semente roda em `onCreate` e o arquivo continua existindo depois de um
     * `DELETE`, então sem repor as categorias o usuário voltaria ao onboarding
     * com o grid de lançamento vazio — e sem jeito de criar a primeira despesa
     * em três toques (Art. 18). Na mesma transação do apagar: metade apagada e
     * metade semeada é pior que qualquer um dos dois.
     *
     * ponytail: repõe categoria, não `payee_rule`. As regras de pagador caem por
     * `CASCADE` junto das categorias e não voltam, o que deixa este estado
     * diferente de uma instalação limpa — invisível hoje, porque nada as lê
     * antes da F2. `regrasSemeadas()` já existe em `Seed.kt`: quando a T-040 der
     * um leitor a elas, é uma linha aqui.
     */
    @Transaction
    open suspend fun apagarTudo() {
        limpar()
        inserirCategorias(categoriasSemeadas())
    }

    /**
     * REQ-BAK-003 — a base do arquivo entra no lugar da atual, de uma vez.
     *
     * Categoria antes de regra e de teto, e conta antes de todas: inserir na
     * ordem errada é FK violada no primeiro `INSERT`, com a base já apagada.
     */
    @Transaction
    open suspend fun substituir(
        contas: List<AccountEntity>,
        categorias: List<CategoryEntity>,
        regras: List<RecurringRuleEntity>,
        tetos: List<BudgetEntity>,
        txns: List<TxnEntity>,
    ) {
        limpar()
        inserirContas(contas)
        inserirCategorias(categorias)
        inserirRegras(regras)
        inserirTetos(tetos)
        inserirTxns(txns)
    }
}

/**
 * Aprendizado por estabelecimento. REQ-ACT-001 · REQ-ACT-002 ·
 * [ingestao.md](../../../../../../../../docs/ingestao.md) §4
 *
 * Sem ML e sem serviço externo: memória das escolhas do usuário. As despesas de
 * uma pessoa se repetem, e um classificador de verdade não bate isso o
 * suficiente para justificar o custo.
 *
 * Classe abstrata por causa de [aprender]: "atualiza, e insere se não existia"
 * são duas escritas que precisam da mesma transação. Fora dela, duas gravações
 * simultâneas violariam o índice único de `normalizedKey`.
 */
@Dao
abstract class PayeeRuleDao {

    /**
     * A regra que casa com esta descrição normalizada, se houver.
     * REQ-ACT-002 · REQ-ACT-003
     *
     * **Por palavra contida, não por igualdade.** As regras semeadas são
     * palavras-chave — `IFOOD`, `UBER`, `NETFLIX` —, e a descrição que chega do
     * banco é `IFOOD PEDIDO` ou `UBER TRIP`. Igualdade exata faria as quarenta
     * sementes nunca casarem com nada, e a primeira importação chegaria vazia,
     * que é justamente o que REQ-ACT-003 existe para evitar.
     *
     * Os espaços em volta dos dois lados são o que impede casamento no meio de
     * palavra: sem eles, a chave `UBER` acharia `SUBERBIA`. Como `normalize` só
     * deixa `[A-Z0-9 ]`, não há curinga de `LIKE` para escapar.
     *
     * A chave **mais longa** ganha: entre a semente `UBER` e a regra aprendida
     * `UBER TRIP AEROPORTO`, a segunda é a que o usuário ensinou, e a mais
     * específica é a que descreve melhor o que ele fez.
     */
    @Query(
        """
        SELECT categoryId FROM payee_rule
        WHERE ' ' || :chave || ' ' LIKE '%' || ' ' || normalizedKey || ' ' || '%'
        ORDER BY length(normalizedKey) DESC
        LIMIT 1
        """,
    )
    abstract suspend fun categoriaDe(chave: String): Long?

    @Query("SELECT * FROM payee_rule WHERE normalizedKey = :chave")
    abstract suspend fun porChave(chave: String): PayeeRuleEntity?

    /**
     * A **última** escolha manda, e o contador conta quantas vezes o par
     * apareceu.
     *
     * `normalizedKey` é único, então uma chave tem uma categoria — e quando o
     * usuário corrige uma sugestão, o que ele disse foi "não é isso, é aquilo".
     * Guardar a maioria histórica em vez da última escolha faria a correção
     * precisar de três repetições para valer, e o usuário desistiria antes.
     */
    @Query(
        """
        UPDATE payee_rule SET categoryId = :categoryId, hitCount = hitCount + 1
        WHERE normalizedKey = :chave
        """,
    )
    abstract suspend fun reforcar(chave: String, categoryId: Long): Int

    @Insert
    abstract suspend fun inserir(regra: PayeeRuleEntity)

    @Transaction
    open suspend fun aprender(chave: String, categoryId: Long) {
        if (reforcar(chave, categoryId) == 0) {
            inserir(PayeeRuleEntity(normalizedKey = chave, categoryId = categoryId))
        }
    }
}
