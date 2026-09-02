package app.financepro.data.ingest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.financepro.data.db.ImportBatchDao
import app.financepro.data.db.DesfeitoDoLote
import app.financepro.data.db.ImportBatchEntity
import app.financepro.data.db.TxnDao
import app.financepro.data.db.TxnEntity
import app.financepro.data.repo.PayeeRuleRepository
import app.financepro.domain.model.TxnType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A borda impura da importação. REQ-IMP-001 · REQ-IMP-010 · REQ-IMP-011
 *
 * Lê o `Uri`, carrega o histórico da conta e grava o lote. As três regras —
 * qual formato, qual coluna, e o que é duplicata — são funções puras noutros
 * arquivos deste pacote, e é por isso que elas têm teste sem emulador.
 *
 * **Nada aqui grava sozinho.** [gravar] só é chamado depois do "confirmar" da
 * tela de revisão (Art. 14, REQ-IMP-010): um app que inventa lançamento perde a
 * confiança na primeira vez que erra, e não tem segunda chance.
 */
@Singleton
class ImportRepository @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val txns: TxnDao,
    private val lotes: ImportBatchDao,
    private val pagadores: PayeeRuleRepository,
) {

    /**
     * Os bytes do arquivo escolhido. REQ-IMP-001
     *
     * Bytes, e não texto: OFX declara o charset dentro dele (REQ-IMP-003), e
     * quem decodifica antes de ler o cabeçalho já perdeu o acento.
     *
     * `ACTION_OPEN_DOCUMENT` é de quem chama; aqui só se abre o que o seletor do
     * sistema já autorizou. Por isso o app não pede `READ_EXTERNAL_STORAGE` nem
     * varre diretório nenhum.
     */
    suspend fun ler(origem: Uri): ByteArray = withContext(Dispatchers.IO) {
        contexto.contentResolver.openInputStream(origem)?.use { it.readBytes() }
            ?: error("não consegui abrir o arquivo escolhido")
    }

    /**
     * O nome que o seletor mostra, e não o que o `Uri` parece dizer.
     *
     * `lastPathSegment` de um `Uri` do SAF devolve o **id do documento** —
     * `msf:39`, `raw:/storage/...` —, que é o que a tela de lotes (T-042)
     * mostraria para o usuário identificar de onde veio a importação. O nome de
     * verdade só sai de `DISPLAY_NAME`, perguntando ao provedor.
     */
    suspend fun nomeDe(origem: Uri): String = withContext(Dispatchers.IO) {
        contexto.contentResolver
            .query(origem, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: origem.lastPathSegment.orEmpty()
    }

    /** O histórico da conta, no formato que o motor de dedupe compara. */
    suspend fun jaGravadas(accountId: Long): List<JaGravada> =
        txns.daConta(accountId).map {
            JaGravada(
                id = it.id,
                date = java.time.LocalDate.ofEpochDay(it.date),
                amountCents = it.amountCents,
                description = it.description,
                dedupeKey = it.dedupeKey,
            )
        }

    /** Os lotes já importados, do mais recente para o mais antigo. REQ-IMP-011 */
    fun observeLotes(): Flow<List<ImportBatchEntity>> = lotes.observeAll()

    /** REQ-IMP-011 — a válvula de escape: o lote inteiro volta atrás. */
    suspend fun desfazer(loteId: Long): DesfeitoDoLote = lotes.desfazer(loteId)

    /** REQ-ACT-002 — a categoria que o app já aprendeu para esta descrição. */
    suspend fun sugerir(descricao: String): Long? = pagadores.sugerir(descricao)

    /**
     * Grava o lote confirmado. REQ-IMP-010 · REQ-IMP-011
     *
     * O `dedupeKey` vai junto de propósito: é a rede de segurança de REQ-IMP-012
     * — o índice único recusa a duplicata mesmo se as três checagens em código
     * falharem — e é o que faz a **próxima** importação reconhecer estas linhas.
     *
     * O aprendizado de estabelecimento roda linha a linha depois da escrita, e
     * não dentro dela: são gravações na tabela de regras, e prendê-las à mesma
     * transação faria um lote de trezentas linhas segurar o banco por todas elas.
     */
    suspend fun gravar(
        accountId: Long,
        origem: String,
        tipo: String,
        linhas: List<LinhaParaGravar>,
    ): Int {
        val agora = System.currentTimeMillis()
        val entidades = linhas.map { linha ->
            TxnEntity(
                accountId = accountId,
                categoryId = linha.categoryId,
                type = if (linha.candidata.amountCents >= 0) TxnType.INCOME else TxnType.EXPENSE,
                amountCents = linha.candidata.amountCents,
                date = linha.candidata.date.toEpochDay(),
                description = linha.candidata.description,
                dedupeKey = linha.candidata.dedupeKey,
                createdAt = agora,
                updatedAt = agora,
            )
        }
        lotes.gravar(
            ImportBatchEntity(
                accountId = accountId,
                sourceType = tipo,
                sourceName = origem,
                importedAt = agora,
                txnCount = entidades.size,
            ),
            entidades,
        )
        linhas.forEach { pagadores.aprender(it.candidata.description, it.categoryId) }
        return entidades.size
    }
}

/** Uma linha que o usuário confirmou, com a categoria que ele deixou nela. */
data class LinhaParaGravar(val candidata: Candidata, val categoryId: Long?)
