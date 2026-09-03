package app.financepro.data.repo

import app.financepro.core.time.PERIODO_TODO
import app.financepro.data.db.ACCOUNT_CASH_COLOR
import app.financepro.data.db.ACCOUNT_CHECKING_COLOR
import app.financepro.data.db.AccountDao
import app.financepro.data.db.AccountEntity
import app.financepro.data.db.BudgetDao
import app.financepro.data.db.BudgetEntity
import app.financepro.data.db.CategoryDao
import app.financepro.data.db.CategoryEntity
import app.financepro.data.db.LIGHT_GREEN
import app.financepro.data.db.NOME_RENDIMENTOS
import app.financepro.data.db.PayeeRuleDao
import app.financepro.data.db.RecurringDao
import app.financepro.data.db.RecurringRuleEntity
import app.financepro.data.db.TxnDao
import app.financepro.data.db.TxnEntity
import app.financepro.data.db.toDomain
import app.financepro.data.ingest.normalize
import app.financepro.domain.model.Account
import app.financepro.domain.model.AccountType
import app.financepro.domain.model.Budget
import app.financepro.domain.model.Category
import app.financepro.domain.model.CategoryKind
import app.financepro.domain.model.Txn
import app.financepro.domain.usecase.RecurringRule
import app.financepro.domain.usecase.occurrenceAt
import app.financepro.domain.usecase.pendingOccurrences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import app.financepro.data.db.toYearMonthInt
import java.time.LocalDate
import java.time.YearMonth
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
            indexador = conta.indexador,
            taxaBp = conta.taxaBp,
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

    /**
     * A categoria que recebe o rendimento, criada na primeira vez. REQ-INV-003
     *
     * Acha por nome e tipo em vez de por id fixo — ver o KDoc de
     * [NOME_RENDIMENTOS]. Se o usuário renomear ou arquivar a dele, a próxima
     * chamada cria outra: duplicar uma categoria é recuperável em dois toques,
     * e um lançamento que não grava não é.
     */
    suspend fun idDeRendimentos(): Long =
        dao.byNome(NOME_RENDIMENTOS, CategoryKind.INCOME.name)?.id
            ?: dao.upsert(
                CategoryEntity(
                    name = NOME_RENDIMENTOS,
                    kind = CategoryKind.INCOME,
                    iconKey = "cash",
                    colorArgb = LIGHT_GREEN,
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
class BudgetRepository @Inject constructor(private val dao: BudgetDao) {

    fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    /**
     * Define o teto de uma categoria num mês. REQ-BUD-001
     *
     * Lê antes de gravar para reusar o id do par: sem isso o `@Upsert` cairia
     * num `UPDATE` por chave primária que não casa com nada, e o índice único
     * recusaria a linha — "no máximo um teto por par" viraria "no máximo um, e
     * a segunda tentativa quebra".
     *
     * Teto zero ou negativo é recusado aqui, na borda de escrita: "não gaste
     * nada nesta categoria" não é um teto, é a ausência dele — e é `remover` que
     * diz isso. A regra também mantém `percent` livre de divisão por zero.
     */
    suspend fun definir(categoryId: Long, mes: YearMonth, limitCents: Long) {
        require(limitCents > 0) { "teto deve ser maior que zero, recebido $limitCents" }
        val existente = dao.byCategoryAndMonth(categoryId, mes.toYearMonthInt())
        dao.upsert(
            BudgetEntity(
                id = existente?.id ?: 0,
                categoryId = categoryId,
                yearMonth = mes.toYearMonthInt(),
                limitCents = limitCents,
            ),
        )
    }

    suspend fun remover(categoryId: Long, mes: YearMonth) {
        dao.byCategoryAndMonth(categoryId, mes.toYearMonthInt())?.let { dao.delete(it) }
    }

    /**
     * Copia para [mes] os tetos do mês anterior. REQ-BUD-005 · Devolve quantos.
     *
     * **Não sobrescreve** o que já existe no mês de destino. "Copiar todos" que
     * apaga um teto ajustado à mão é uma ação destrutiva escondida atrás de um
     * botão de conveniência — quem já decidiu que este mês tem outro teto disse
     * mais do que o mês passado.
     *
     * Uma escrita só: metade dos tetos copiados é um mês pela metade, e o botão
     * não diz qual metade.
     */
    suspend fun copiarDoMesAnterior(mes: YearMonth): Int {
        val destino = mes.toYearMonthInt()
        val jaTem = dao.doMes(destino).map { it.categoryId }.toSet()
        val novos = dao.doMes(mes.minusMonths(1).toYearMonthInt())
            .filter { it.categoryId !in jaTem }
            .map { BudgetEntity(categoryId = it.categoryId, yearMonth = destino, limitCents = it.limitCents) }
        if (novos.isNotEmpty()) dao.upsertAll(novos)
        return novos.size
    }
}

/**
 * Aprendizado por estabelecimento. REQ-ACT-001 · REQ-ACT-002 · REQ-ACT-004
 *
 * A chave é a **mesma** `normalize` do dedupe, e é isso que REQ-ACT-004 exige:
 * duas normalizações divergentes fariam a importação descartar como duplicata o
 * que o aprendizado trata como loja nova.
 */
@Singleton
class PayeeRuleRepository @Inject constructor(private val dao: PayeeRuleDao) {

    /**
     * A categoria que o app sugere para esta descrição, se já souber.
     * REQ-ACT-002
     *
     * Nula é resposta legítima e não um erro: a tela de revisão destaca a linha
     * como sem categoria, e é o usuário quem ensina — a próxima importação já
     * vem preenchida.
     *
     * O casamento é por **palavra contida**, e a chave mais longa ganha: as
     * regras semeadas são palavras-chave e as aprendidas são descrições
     * inteiras. Ver o KDoc de `PayeeRuleDao.categoriaDe`.
     */
    suspend fun sugerir(descricao: String): Long? =
        normalize(descricao).takeIf { it.isNotBlank() }?.let { dao.categoriaDe(it) }

    /**
     * Guarda o que o usuário escolheu. REQ-ACT-001
     *
     * Categoria nula não ensina nada — transferência não tem categoria
     * (REQ-TXN-004) —, e descrição que normaliza para vazio ensina pior ainda:
     * a chave `""` casaria com toda linha sem descrição de todo extrato futuro,
     * e o app passaria a sugerir uma categoria para tudo.
     */
    suspend fun aprender(descricao: String, categoryId: Long?) {
        val chave = normalize(descricao)
        if (categoryId == null || chave.isBlank()) return
        dao.aprender(chave, categoryId)
    }
}

@Singleton
class TxnRepository @Inject constructor(
    private val dao: TxnDao,
    private val pagadores: PayeeRuleRepository,
) {

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
    /**
     * O Room roda o `SELECT` no executor dele, mas o `.map` para o domínio roda
     * em quem coleta — e quem coleta é o `viewModelScope`, na thread principal.
     * Com o histórico inteiro (REQ-ACC-003) isso é uma alocação por linha por
     * emissão, na thread que desenha. `flowOn` empurra o mapeamento para trás
     * dele; a emissão continua chegando ao coletor onde sempre chegou.
     */
    fun observeBetween(de: LocalDate, ate: LocalDate): Flow<List<Txn>> =
        dao.observeBetween(de.toEpochDay(), ate.toEpochDay())
            .map { l -> l.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

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
            .flowOn(Dispatchers.Default)

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
     * `recurringRuleId`, `importBatchId` e `dedupeKey` — as três colunas que o
     * domínio não carrega (Art. 8) — e reescreveria o `createdAt`. `notes` saiu
     * dessa lista na T-052, quando a folha ganhou onde escrevê-la.
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
        val id = dao.upsert(existente?.aplicar(txn, agora) ?: txn.toEntity(agora))
        // REQ-ACT-001 — o aprendizado mora **aqui**, e não em quem chama, pela
        // mesma razão da leitura-antes-de-escrever logo acima: é por aqui que
        // passa todo chamador, inclusive os que ainda não existem. Corrigir a
        // categoria de uma transação importada é justamente o momento em que o
        // app mais tem a aprender, e é um caminho que não passa por tela nova.
        pagadores.aprender(txn.description, txn.categoryId)
        return id
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
        // Uma compra, um aprendizado: as doze parcelas são o mesmo
        // estabelecimento, e contá-las doze vezes inflaria o `hitCount` sem
        // ensinar nada de novo.
        parcelas.firstOrNull()?.let { pagadores.aprender(it.description, it.categoryId) }
    }

    /**
     * Exclui, guardando a linha para o desfazer de 5s. REQ-TXN-010
     *
     * A entidade fica **aqui**, e não no ViewModel, por uma razão concreta:
     * `TxnEntity` tem `recurringRuleId`, `importBatchId`, `dedupeKey` e
     * `createdAt`, que o modelo de domínio não carrega. Desfazer a partir de um
     * `Txn` apagaria as quatro em silêncio — e uma `dedupeKey` perdida faria a
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
 * Regras de recorrência e a materialização delas.
 * REQ-REC-001 · REQ-REC-003 · REQ-REC-004 · REQ-REC-005 · REQ-REC-007
 *
 * A borda impura da [pendingOccurrences]: aqui moram o relógio, o banco e a
 * transação. A decisão de **quais** datas gerar continua pura e testável em
 * JVM, que é o que o Art. 9 pede.
 */
@Singleton
class RecurringRepository @Inject constructor(private val dao: RecurringDao) {

    fun observeAll(): Flow<List<RecurringRule>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    /**
     * Materializa o que falta em todas as regras ativas. Devolve quantas
     * linhas nasceram. REQ-REC-003 · REQ-REC-004 · REQ-REC-005
     *
     * Roda na abertura do app, e roda quantas vezes for: a segunda execução do
     * mesmo dia não acha ocorrência pendente e devolve zero.
     *
     * Uma transação **por regra**, e não uma para todas: uma regra com data
     * inválida não pode desfazer a geração das outras, e cada
     * `lastGeneratedDate` só faz sentido junto das linhas daquela regra.
     */
    suspend fun gerarPendentes(hoje: LocalDate): Int {
        val agora = System.currentTimeMillis()
        var criadas = 0
        for (linha in dao.ativas()) {
            val regra = linha.toDomain()
            val datas = pendingOccurrences(regra, hoje)
            if (datas.isEmpty()) continue
            dao.materializar(
                ruleId = regra.id,
                txns = datas.map {
                    regra.occurrenceAt(it).toEntity(agora).copy(recurringRuleId = regra.id)
                },
                ate = datas.last().toEpochDay(),
            )
            criadas += datas.size
        }
        return criadas
    }

    /**
     * Cria ou altera a regra, e deixa as ocorrências coerentes com ela.
     * REQ-REC-001 · REQ-REC-007 · Devolve o id.
     *
     * Alterar reescreve: as futuras não efetivadas saem, a marca recua até a
     * última que sobrou, e a geração logo abaixo repõe pela regra nova. O que
     * já foi efetivado não é tocado — mudar o valor do aluguel hoje não
     * reescreve o que foi pago em março.
     *
     * A geração vem junto porque o contrário seria uma tela mostrando uma regra
     * nova sem nenhuma próxima conta até o app ser reaberto.
     */
    suspend fun salvar(regra: RecurringRule, hoje: LocalDate): Long {
        val id = dao.salvar(regra.toEntity(), hoje.toEpochDay())
        gerarPendentes(hoje)
        return id
    }

    /**
     * Apaga a regra e deixa as ocorrências de pé.
     *
     * `txn.recurringRuleId` é `SET_NULL`: o aluguel pago em março continua no
     * extrato depois de o aluguel deixar de ser recorrente. Apagar o histórico
     * junto mudaria o saldo de um mês fechado, que é o que REQ-REC-007 recusa
     * até para uma simples edição.
     *
     * Quem quer parar de gerar **sem** apagar nada usa `active = false`, que é
     * o que a tela oferece primeiro.
     */
    suspend fun excluir(regra: RecurringRule) {
        dao.byId(regra.id)?.let { dao.delete(it) }
    }
}

/**
 * Domínio → entidade. Ver [RecurringRepository.salvar].
 *
 * O `spec` desmonta nas colunas soltas que o schema tem: `freq` como texto,
 * `weekday` como inteiro ISO (1 = segunda), datas como `epochDay`. A volta é a
 * `toDomain` de `Mappers.kt`, e as duas juntas são o contrato do formato.
 */
private fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id,
    accountId = accountId,
    counterAccountId = counterAccountId,
    categoryId = categoryId,
    type = type,
    amountCents = amountCents,
    description = description,
    freq = spec.frequency.name,
    interval = spec.interval,
    dayOfMonth = spec.dayOfMonth,
    weekday = spec.weekday?.value,
    monthOfYear = spec.monthOfYear,
    startDate = spec.startDate.toEpochDay(),
    endDate = spec.endDate?.toEpochDay(),
    lastGeneratedDate = lastGeneratedDate?.toEpochDay(),
    autoPost = autoPost,
    active = active,
)

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
    notes = txn.notes,
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
    notes = notes,
    cleared = cleared,
    installmentGroupId = installmentGroupId,
    installmentIndex = installmentIndex,
    installmentTotal = installmentTotal,
    createdAt = agora,
    updatedAt = agora,
)
