package app.financepro.data.export

import android.content.Context
import android.net.Uri
import app.financepro.data.db.BackupDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup, restauração e apagar tudo. REQ-BAK-002 · REQ-BAK-003 · REQ-BAK-004
 *
 * Separado do [ExportRepository] porque as operações são de naturezas
 * diferentes: exportar é leitura, e o pior que acontece é um arquivo a mais no
 * disco. Aqui tudo é destrutivo ou irreversível — restaurar troca a base
 * inteira, apagar não volta, e um backup com senha errada é um arquivo perdido.
 *
 * A derivação da chave leva segundos de propósito ([ITERACOES_PBKDF2]), então
 * **toda** operação sai da thread principal, inclusive as que não fazem I/O.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val exportacao: ExportRepository,
    private val dao: BackupDao,
) {

    /** Quantos registros a base tem agora. É o número que a confirmação exibe. */
    suspend fun quantosRegistros(): Int = exportacao.base().registros

    /** Cifra a base inteira no destino escolhido. Devolve quantos registros foram. */
    suspend fun criar(destino: Uri, senha: CharArray): Int {
        val base = exportacao.base()
        val arquivo = withContext(Dispatchers.Default) { cifrarBackup(base, senha) }
        withContext(Dispatchers.IO) {
            contexto.contentResolver.openOutputStream(destino)?.use { it.write(arquivo) }
                ?: error("não consegui abrir o arquivo escolhido")
        }
        return base.registros
    }

    /**
     * Lê e decifra, **sem** tocar no banco. REQ-BAK-003
     *
     * A leitura é separada da escrita de propósito: é o que permite a tela
     * dizer quantos registros vêm e quantos serão substituídos **antes** de
     * qualquer coisa ser sobrescrita. Um "restaurar" de um passo só teria de
     * escolher entre pedir confirmação sem saber o conteúdo, ou apagar para
     * depois descobrir que a senha estava errada.
     */
    suspend fun ler(origem: Uri, senha: CharArray): BaseExportada {
        val bytes = withContext(Dispatchers.IO) {
            contexto.contentResolver.openInputStream(origem)?.use { it.readBytes() }
                ?: throw BackupIlegivel("não consegui abrir o arquivo escolhido")
        }
        return withContext(Dispatchers.Default) { decifrarBackup(bytes, senha) }
    }

    /** Troca a base pela do arquivo, numa transação só. REQ-BAK-003 */
    suspend fun restaurar(base: BaseExportada) = dao.substituir(
        contas = base.accounts,
        categorias = base.categories,
        regras = base.recurringRules,
        tetos = base.budgets,
        txns = base.txns,
    )

    /**
     * REQ-BAK-004 — apaga tudo, e não recria nada.
     *
     * Sem contas o app volta ao onboarding sozinho, porque "sem conta nenhuma"
     * **é** a definição de primeiro uso (ver `RaizViewModel`). É por isso que
     * apagar não precisa de um estado "app zerado" à parte para lembrar.
     */
    suspend fun apagarTudo() = dao.apagarTudo()
}
