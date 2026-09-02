package app.financepro.data.indices

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Busca o CDI uma vez por dia. REQ-INV-005
 *
 * Diário porque é o passo do dado: o BCB publica a série em dia útil, e uma
 * segunda busca no mesmo dia traria o mesmo número. O [ADR-006](../../../../../../../../docs/decisoes.md)
 * recusou um worker diário para recorrências com o argumento de que nada no app
 * lia o resultado com ele fechado — aqui o argumento não se aplica: o CDI muda
 * fora do app, sem o usuário fazer nada, e é justamente por isso que ele não
 * deveria precisar abrir a tela para o número ficar certo.
 *
 * **Sem `androidx.hilt:hilt-work`.** O worker precisa de uma coisa só — o
 * DataStore —, e `IndicesPrefs(applicationContext)` chega nele: o delegate
 * `by preferencesDataStore(name = "indices")` garante uma única instância do
 * arquivo por processo, independente de quantas vezes a classe for construída.
 * Uma `HiltWorkerFactory` custaria dependência nova, `Configuration.Provider` na
 * `FinanceApp` e a remoção do inicializador padrão no manifesto, para injetar
 * um construtor de um parâmetro.
 */
class CdiWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /**
     * `retry` e não `failure` quando a busca não vem: a restrição de rede diz
     * que **havia** conexão quando o trabalho começou, então falhar aqui é
     * quase sempre servidor momentaneamente fora — e `failure` desistiria até
     * a próxima janela de 24 horas.
     */
    override suspend fun doWork(): Result {
        val cdi = buscarCdi() ?: return Result.retry()
        IndicesPrefs(applicationContext).guardar(cdi)
        return Result.success()
    }

    companion object {
        private const val NOME = "cdi-diario"

        /**
         * `KEEP` e não `REPLACE`: `FinanceApp.onCreate` roda a cada lançamento
         * do app, e `REPLACE` reagendaria o trabalho toda vez — empurrando a
         * próxima execução para 24 horas adiante em quem abre o app todo dia,
         * que é exatamente quem nunca veria o CDI atualizar.
         */
        fun agendar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<CdiWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
