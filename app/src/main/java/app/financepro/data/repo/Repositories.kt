package app.financepro.data.repo

import app.financepro.core.time.PERIODO_TODO
import app.financepro.data.db.ACCOUNT_CASH_COLOR
import app.financepro.data.db.ACCOUNT_CHECKING_COLOR
import app.financepro.data.db.AccountDao
import app.financepro.data.db.AccountEntity
import app.financepro.data.db.CategoryDao
import app.financepro.data.db.CategoryEntity
import app.financepro.data.db.TxnDao
import app.financepro.data.db.TxnEntity
import app.financepro.data.db.toDomain
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositórios. A fronteira onde a entidade do Room vira modelo de domínio.
 *
 * **Sem interface** (Art. 10, arquitetura.md §3). Não existe segunda
 * implementação, e para trocar por fake em teste o Hilt substitui o módulo
 * inteiro — a interface não compraria nada e custaria um arquivo por
 * repositório, para sempre.
 *
 * Leitura devolve `Flow`: a UI observa, nunca busca imperativamente, e uma
 * transação nova reflete na lista, no saldo e no dashboard sem ninguém
 * orquestrar recarga.
 *
 * **Escrita nasce com a tela que a chama.** Não há `upsert(conta)` genérico: o
 * onboarding cria exatamente duas contas e o lançamento rápido grava exatamente
 * uma transação. Um CRUD completo escrito antes de T-015 e T-016 seria adivinhar
 * três assinaturas e acertar por acaso.
 */

@Singleton
class AccountRepository @Inject constructor(private val dao: AccountDao) {

    fun observeActive(): Flow<List<Account>> = dao.observeActive().map { l -> l.map { it.toDomain() } }

    fun observeAll(): Flow<List<Account>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Account? = dao.byId(id)?.toDomain()

    /**
     * REQ-UI-005 — as duas contas do onboarding, numa transação só.
     *
     * `CASH` nasce zerada e `CHECKING` com o saldo informado. É o que faz o app
     * ser utilizável em menos de 30 segundos: quem instala tem dinheiro na
     * carteira e dinheiro no banco, e não precisa cadastrar nada antes de gastar.
     */
    /**
     * Cria ou atualiza. REQ-ACC-001
     *
     * Recebe o modelo de domínio inteiro porque a tela de contas edita todos os
     * campos que ele tem — não sobra nada de apresentação para o repositório
     * adivinhar, que era o motivo de não existir escrita genérica antes.
     */
    suspend fun salvar(conta: Account): Long = dao.upsert(
        AccountEntity(
            id = conta.id,
            name = conta.name,
            type = conta.type,
            initialBalanceCents = conta.initialBalanceCents,
            colorArgb = conta.colorArgb,
            iconKey = conta.iconKey,
            archived = conta.archived,
            sortOrder = conta.sortOrder,
            creditLimitCents = conta.creditLimitCents,
            closingDay = conta.closingDay,
            dueDay = conta.dueDay,
            paymentAccountId = conta.paymentAccountId,
        ),
    )

    /**
     * REQ-ACC-005 — arquivar tira das listas e do saldo, e **preserva** o
     * histórico. É por isso que arquivar existe em vez de excluir: apagar a
     * conta levaria as transações junto por `CASCADE`, e o relatório do ano
     * passado mudaria sozinho.
     */
    suspend fun arquivar(conta: Account, arquivada: Boolean = true) =
        salvar(conta.copy(archived = arquivada))

    suspend fun criarIniciais(saldoCents: Long) {
        dao.upsert(
            AccountEntity(
                name = "Carteira",
                type = AccountType.CASH,
                colorArgb = ACCOUNT_CASH_COLOR,
                iconKey = "wallet",
                sortOrder = 0,
            ),
        )
        dao.upsert(
            AccountEntity(
                name = "Conta corrente",
                type = AccountType.CHECKING,
                initialBalanceCents = saldoCents,
                colorArgb = ACCOUNT_CHECKING_COLOR,
                iconKey = "bank",
                sortOrder = 1,
            ),
        )
    }
}

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val txns: TxnDao,
) {

    fun observeActive(): Flow<List<Category>> = dao.observeActive().map { l -> l.map { it.toDomain() } }

    /** Ordem do grid do lançamento rápido, mais usadas primeiro (REQ-CAT-006). */
    fun observeByUse(kind: CategoryKind): Flow<List<Category>> =
        dao.observeByUse(kind.name).map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Category? = dao.byId(id)?.toDomain()

    /** REQ-CAT-006 — cada salvamento empurra a categoria para cima no grid. */
    suspend fun registrarUso(id: Long) = dao.bumpUse(id)

    /** REQ-CAT-001 · REQ-CAT-002 — a checagem de um nível vem junto. */
    suspend fun salvar(categoria: Category): Long = dao.upsertChecked(
        CategoryEntity(
            id = categoria.id,
            name = categoria.name,
            kind = categoria.kind,
            parentId = categoria.parentId,
            iconKey = categoria.iconKey,
            colorArgb = categoria.colorArgb,
            archived = categoria.archived,
        ),
    )

    /** Quantas transações impedem a exclusão (REQ-CAT-005). */
    suspend fun transacoesEm(id: Long): Int = dao.contarTransacoes(id)

    /**
     * Exclui, movendo o que segurava a categoria. REQ-CAT-005
     *
     * `destino` nulo só é aceito quando não há nada a mover: sem isso, "excluir
     * sem escolher para onde" viraria transação sem categoria, que é
     * exatamente o que REQ-TXN-005 proíbe.
     */
    suspend fun excluir(categoria: Category, destino: Long?) {
        val presas = transacoesEm(categoria.id)
        require(presas == 0 || destino != null) {
            "Mova as $presas transações antes"
        }
        if (destino != null && presas > 0) txns.recategorizar(categoria.id, destino)
        dao.byId(categoria.id)?.let { dao.delete(it) }
    }
}

@Singleton
class TxnRepository @Inject constructor(private val dao: TxnDao) {

    /**
     * As linhas da última exclusão, à espera do desfazer. Ver [excluir].
     *
     * Lista, e não uma linha: excluir uma compra parcelada com escopo "todas"
     * (REQ-TXN-009) apaga doze de uma vez, e um desfazer que repõe uma delas
     * seria pior que nenhum.
     */
    @Volatile
    private var ultimaExcluida: List<TxnEntity> = emptyList()

    /**
     * O intervalo chega como `LocalDate` e vira `epochDay` aqui: a conversão é
     * de borda, e nenhuma tela precisa saber que a coluna é inteiro.
     */
    fun observeBetween(de: LocalDate, ate: LocalDate): Flow<List<Txn>> =
        dao.observeBetween(de.toEpochDay(), ate.toEpochDay()).map { l -> l.map { it.toDomain() } }

    /**
     * O histórico inteiro, para quem calcula saldo.
     *
     * Saldo é de todos os tempos por definição (REQ-ACC-003), e três telas
     * pediam isso soletrando a mesma janela larga cada uma à sua maneira. A
     * janela em si está em [PERIODO_TODO], com o `ponytail:` que a explica.
     */
    fun observeTudo(): Flow<List<Txn>> = observeBetween(PERIODO_TODO.start, PERIODO_TODO.endInclusive)

    fun observeByAccount(accountId: Long, de: LocalDate, ate: LocalDate): Flow<List<Txn>> =
        dao.observeByAccount(accountId, de.toEpochDay(), ate.toEpochDay())
            .map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Txn? = dao.byId(id)?.toDomain()

    /**
     * Grava a transação e devolve o id. REQ-TXN-001
     *
     * `createdAt`/`updatedAt` saem do relógio **aqui**, não no domínio: é a borda
     * impura, e um `System.currentTimeMillis()` dentro de função pura tornaria o
     * teste dela dependente do instante em que roda.
     *
     * Com `id != 0` isto é uma **edição**, e a linha antiga é lida antes de ser
     * sobrescrita. Montar a entidade do zero, como no caminho de criação, zeraria
     * `notes`, `recurringRuleId`, `importBatchId` e `dedupeKey` — as quatro
     * colunas que o domínio não carrega (Art. 8) — e reescreveria o `createdAt`.
     * O `@Upsert` faria isso em silêncio: um `UPDATE` bem-sucedido, com quatro
     * colunas a menos. É o mesmo cuidado de [excluir], pela mesma razão: uma
     * `dedupeKey` perdida faz a importação da F2 recriar a transação como nova.
     *
     * A guarda mora aqui, e não em quem edita, porque é por aqui que passa todo
     * chamador — inclusive os que ainda não existem.
     */
    suspend fun salvar(txn: Txn): Long {
        val agora = System.currentTimeMillis()
        val existente = txn.id.takeIf { it != 0L }?.let { dao.byId(it) }
        return dao.upsert(existente?.aplicar(txn, agora) ?: txn.toEntity(agora))
    }

    /**
     * Grava as N parcelas como um grupo. REQ-TXN-007
     *
     * O `installmentGroupId` nasce **aqui**: é aleatório, e sortear identificador
     * dentro do ViewModel tornaria o teste dele dependente do sorteio. Uma
     * escrita só para as N linhas — meia compra parcelada é dinheiro inventado
     * no extrato.
     */
    suspend fun salvarParcelado(parcelas: List<Txn>) {
        val grupo = UUID.randomUUID().toString()
        val agora = System.currentTimeMillis()
        dao.upsertAll(parcelas.map { it.toEntity(agora).copy(installmentGroupId = grupo) })
    }

    /**
     * Exclui, guardando a linha para o desfazer de 5s. REQ-TXN-010
     *
     * A entidade fica **aqui**, e não no ViewModel, por uma razão concreta:
     * `TxnEntity` tem `notes`, `recurringRuleId`, `importBatchId`, `dedupeKey` e
     * `createdAt`, que o modelo de domínio não carrega. Desfazer a partir de um
     * `Txn` apagaria as cinco em silêncio — e uma `dedupeKey` perdida faria a
     * próxima importação (F2) recriar a transação como se fosse nova.
     *
     * Subir as colunas para o domínio quebraria o Art. 8; devolver a entidade
     * para o ViewModel quebraria a §3 da arquitetura. Guardá-la no repositório
     * não quebra nenhum dos dois.
     *
     * ponytail: guarda **uma** exclusão; a segunda descarta a primeira. É o que
     * o snackbar comporta, que mostra uma de cada vez. Vira pilha se a T-042
     * (desfazer de lote de importação) precisar.
     */
    /**
     * Exclui um escopo de parcelas de uma vez. REQ-TXN-009
     *
     * Uma escrita só, pela mesma razão de [salvarParcelado]: metade de uma
     * compra parcelada apagada é dinheiro inventado no extrato. E um desfazer
     * só, que repõe exatamente o que saiu.
     */
    suspend fun excluirVarias(ids: List<Long>) {
        val alvos = ids.mapNotNull { dao.byId(it) }
        if (alvos.isEmpty()) return
        ultimaExcluida = alvos
        dao.deleteAll(alvos)
    }

    /**
     * Repõe a última exclusão. Devolve `false` se não havia o que desfazer.
     *
     * `insert`, não `upsert`: o id volta idêntico, e as parcelas irmãs do mesmo
     * `installmentGroupId` continuam apontando para o conjunto certo. O sinal é
     * consumido na entrada — desfazer duas vezes repõe uma vez, que é o que a
     * palavra significa.
     */
    suspend fun desfazerExclusao(): Boolean {
        val alvos = ultimaExcluida
        if (alvos.isEmpty()) return false
        ultimaExcluida = emptyList()
        dao.insertAll(alvos)
        return true
    }

    /** As irmãs de uma compra parcelada, em ordem. REQ-TXN-009 */
    suspend fun grupoDeParcelas(groupId: String): List<Txn> =
        dao.installmentGroup(groupId).map { it.toDomain() }

    /**
     * Grava um escopo de parcelas de uma vez. REQ-TXN-009
     *
     * Passa pela mesma leitura-antes-de-escrever de [salvar], e pela mesma
     * razão: sem ela o `@Upsert` zeraria `notes`, `dedupeKey`, `importBatchId`
     * e `recurringRuleId` de doze linhas em silêncio, em vez de uma.
     */
    suspend fun salvarVarias(txns: List<Txn>) {
        val agora = System.currentTimeMillis()
        val linhas = txns.mapNotNull { txn -> dao.byId(txn.id)?.aplicar(txn, agora) }
        if (linhas.isNotEmpty()) dao.upsertAll(linhas)
    }
}

/**
 * Aplica sobre a linha existente **só** o que o domínio carrega.
 * Ver [TxnRepository.salvar].
 *
 * `copy`, e não construtor, é o ponto: o que não está listado aqui sobrevive.
 * Uma coluna nova que a edição não conhece continua intocada em vez de virar
 * `null` na primeira gravação.
 */
private fun TxnEntity.aplicar(txn: Txn, agora: Long) = copy(
    accountId = txn.accountId,
    counterAccountId = txn.counterAccountId,
    categoryId = txn.categoryId,
    type = txn.type,
    amountCents = txn.amountCents,
    date = txn.date.toEpochDay(),
    description = txn.description,
    cleared = txn.cleared,
    installmentGroupId = txn.installmentGroupId,
    installmentIndex = txn.installmentIndex,
    installmentTotal = txn.installmentTotal,
    updatedAt = agora,
)

private fun Txn.toEntity(agora: Long) = TxnEntity(
    id = id,
    accountId = accountId,
    counterAccountId = counterAccountId,
    categoryId = categoryId,
    type = type,
    amountCents = amountCents,
    date = date.toEpochDay(),
    description = description,
    cleared = cleared,
    installmentGroupId = installmentGroupId,
    installmentIndex = installmentIndex,
    installmentTotal = installmentTotal,
    createdAt = agora,
    updatedAt = agora,
)
