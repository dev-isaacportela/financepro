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
import app.financepro.feature.FinanceNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        protegerJanela()
        setContent {
            SlushTheme {
                FinanceNav()
            }
        }
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
