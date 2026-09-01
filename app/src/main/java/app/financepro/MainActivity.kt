package app.financepro

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.financepro.core.ui.theme.SlushTheme
import app.financepro.data.prefs.SecurityPrefs
import app.financepro.data.repo.RecurringRepository
import app.financepro.feature.FinanceNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Só o tema, o grafo de navegação e a proteção da janela. Nenhuma tela mora
 * aqui: a `Activity` é a casca do processo, e conteúdo dentro dela é conteúdo
 * que nenhuma rota alcança.
 *
 * `FragmentActivity` e não `ComponentActivity` porque o `BiometricPrompt` do
 * `androidx.biometric` exige — ele monta um fragmento para sobreviver a
 * mudanças de configuração durante a autenticação (REQ-SEC-003).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var seguranca: SecurityPrefs

    @Inject
    lateinit var recorrencias: RecurringRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        protegerJanela()
        gerarRecorrencias()
        setContent {
            SlushTheme {
                FinanceNav()
            }
        }
    }

    /**
     * REQ-REC-003 — a geração roda na abertura do app.
     *
     * Sem guarda contra rodar de novo, e é de propósito: a idempotência é o
     * requisito, não uma otimização. Uma checagem aqui daria a impressão de que
     * o gerador precisa dela, e o dia em que a rotação da tela recriasse a
     * `Activity` duas vezes já era.
     *
     * ponytail: sem o `WorkManager` diário do ADR-006. Nada no app produz saída
     * com ele fechado — não há notificação de conta a vencer, e a permissão de
     * rede está barrada até a F4 —, então materializar em segundo plano grava
     * linhas que ninguém vê antes da próxima abertura, que é justamente quando
     * isto aqui roda. O worker entra no dia em que existir consumidor de fundo
     * (lembrete de vencimento, widget): é uma classe, e o gerador já está
     * pronto para ser chamado por ela.
     */
    private fun gerarRecorrencias() = lifecycleScope.launch {
        recorrencias.gerarPendentes(LocalDate.now())
    }

    /**
     * REQ-SEC-005 — `FLAG_SECURE` enquanto o bloqueio estiver ligado: sem
     * captura de tela, e janela em branco na lista de recentes.
     *
     * Coleta em vez de ler uma vez: quem liga o bloqueio nos ajustes espera que
     * valha **agora**. Ler só no `onCreate` deixaria a miniatura dos recentes
     * exibindo o saldo até o próximo start do processo, que é exatamente o
     * vazamento que o requisito fecha.
     */
    private fun protegerJanela() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            seguranca.bloqueio.collect { ativo ->
                if (ativo) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}
