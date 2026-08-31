package com.benenutri.finance.data.repo

import com.benenutri.finance.data.db.AccountDao
import com.benenutri.finance.data.db.CategoryDao
import com.benenutri.finance.data.db.TxnDao
import com.benenutri.finance.data.db.toDomain
import com.benenutri.finance.domain.model.Account
import com.benenutri.finance.domain.model.Category
import com.benenutri.finance.domain.model.CategoryKind
import com.benenutri.finance.domain.model.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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
 * **Só leitura, por enquanto.** Cada escrita nasce com a tela que a chama —
 * T-012 cria conta, T-013 cria transação, T-016 mexe em categoria — porque é a
 * tela que define quais colunas de apresentação (`colorArgb`, `iconKey`,
 * `sortOrder`) entram no modelo de domínio. Escrever os `upsert` agora seria
 * adivinhar essa assinatura três vezes e acertar por acaso.
 */

@Singleton
class AccountRepository @Inject constructor(private val dao: AccountDao) {

    fun observeActive(): Flow<List<Account>> = dao.observeActive().map { l -> l.map { it.toDomain() } }

    fun observeAll(): Flow<List<Account>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Account? = dao.byId(id)?.toDomain()
}

@Singleton
class CategoryRepository @Inject constructor(private val dao: CategoryDao) {

    fun observeActive(): Flow<List<Category>> = dao.observeActive().map { l -> l.map { it.toDomain() } }

    /** Ordem do grid do lançamento rápido, mais usadas primeiro (REQ-CAT-006). */
    fun observeByUse(kind: CategoryKind): Flow<List<Category>> =
        dao.observeByUse(kind.name).map { l -> l.map { it.toDomain() } }

    suspend fun byId(id: Long): Category? = dao.byId(id)?.toDomain()
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
}
