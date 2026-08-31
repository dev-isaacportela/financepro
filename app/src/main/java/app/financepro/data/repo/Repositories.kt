package app.financepro.data.repo

import app.financepro.data.db.ACCOUNT_CASH_COLOR
import app.financepro.data.db.ACCOUNT_CHECKING_COLOR
import app.financepro.data.db.AccountDao
import app.financepro.data.db.AccountEntity
import app.financepro.data.db.CategoryDao
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
class CategoryRepository @Inject constructor(private val dao: CategoryDao) {

    fun observeActive(): Flow<List<Category>> = dao.observeActive().map { l -> l.map { it.toDomain() } }

    /** Ordem do grid do lançamento rápido, mais usadas primeiro (REQ-CAT-006). */
    fun observeByUse(kind: CategoryKind): Flow<List<Category>> =
        dao.observeByUse(kind.name).map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Category? = dao.byId(id)?.toDomain()

    /** REQ-CAT-006 — cada salvamento empurra a categoria para cima no grid. */
    suspend fun registrarUso(id: Long) = dao.bumpUse(id)
}

@Singleton
class TxnRepository @Inject constructor(private val dao: TxnDao) {

    /**
     * O intervalo chega como `LocalDate` e vira `epochDay` aqui: a conversão é
     * de borda, e nenhuma tela precisa saber que a coluna é inteiro.
     */
    fun observeBetween(de: LocalDate, ate: LocalDate): Flow<List<Txn>> =
        dao.observeBetween(de.toEpochDay(), ate.toEpochDay()).map { l -> l.map { it.toDomain() } }

    fun observeByAccount(accountId: Long, de: LocalDate, ate: LocalDate): Flow<List<Txn>> =
        dao.observeByAccount(accountId, de.toEpochDay(), ate.toEpochDay())
            .map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Txn? = dao.byId(id)?.toDomain()

    /**
     * Grava a transação e devolve o id.
     *
     * `createdAt`/`updatedAt` saem do relógio **aqui**, não no domínio: é a borda
     * impura, e um `System.currentTimeMillis()` dentro de função pura tornaria o
     * teste dela dependente do instante em que roda.
     */
    suspend fun salvar(txn: Txn): Long = dao.upsert(txn.toEntity(System.currentTimeMillis()))

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
}

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
