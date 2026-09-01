package app.financepro.data.export

import android.content.Context
import android.net.Uri
import app.financepro.data.db.AccountDao
import app.financepro.data.db.BudgetDao
import app.financepro.data.db.CategoryDao
import app.financepro.data.db.RecurringDao
import app.financepro.data.db.TxnDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lê a base e escreve no destino que o usuário escolheu. REQ-BAK-001
 *
 * O `Uri` vem do `ACTION_CREATE_DOCUMENT`, e é a razão de a escrita morar aqui e
 * não na tela: o app não conhece o caminho do arquivo, não pede permissão de
 * armazenamento e não decide onde salvar — quem decide é o seletor do sistema,
 * e o que volta é um `Uri` que só o `ContentResolver` sabe abrir. É também o que
 * mantém REQ-SEC-007 de pé: nada disso passa por rede.
 *
 * Fala com os DAOs direto, sem passar pelos repositórios, porque o que ele
 * precisa é a **entidade** — ver o KDoc de [Export.kt][BaseExportada].
 */
@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val contas: AccountDao,
    private val categorias: CategoryDao,
    private val txns: TxnDao,
    private val tetos: BudgetDao,
    private val regras: RecurringDao,
) {

    /** A base inteira, como ela está no banco. */
    suspend fun base(): BaseExportada = BaseExportada(
        accounts = contas.todas(),
        categories = categorias.todas(),
        txns = txns.todas(),
        budgets = tetos.todas(),
        recurringRules = regras.todas(),
    )

    /** Devolve quantas transações foram para o arquivo. */
    suspend fun exportarCsv(destino: Uri): Int {
        val base = base()
        escrever(destino, paraCsv(base.txns, base.accounts, base.categories))
        return base.txns.size
    }

    /** Devolve quantos registros, de todas as tabelas, foram para o arquivo. */
    suspend fun exportarJson(destino: Uri): Int {
        val base = base()
        escrever(destino, paraJson(base))
        return base.registros
    }

    /**
     * `Dispatchers.IO` porque o `ContentResolver` pode estar do outro lado de um
     * provedor de nuvem: o seletor do sistema oferece Drive e OneDrive junto com
     * o armazenamento local, e escrever nesses num `Dispatchers.Main` trava a
     * tela pelo tempo de um upload.
     *
     * UTF-8 explícito, e não o padrão da plataforma: o BOM do CSV só serve para
     * alguma coisa se os acentos depois dele estiverem em UTF-8.
     */
    private suspend fun escrever(destino: Uri, texto: String) = withContext(Dispatchers.IO) {
        contexto.contentResolver.openOutputStream(destino)?.use {
            it.write(texto.toByteArray(Charsets.UTF_8))
        } ?: error("não consegui abrir o arquivo escolhido")
    }
}
