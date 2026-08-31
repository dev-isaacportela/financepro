package app.financepro.feature.lock

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.financepro.core.ui.component.FilledCta
import app.financepro.core.ui.theme.Body
import app.financepro.core.ui.theme.HeadingSm
import app.financepro.core.ui.theme.Slush

/**
 * A porta do app quando o bloqueio está ligado. REQ-SEC-003
 *
 * Nada de financeiro é composto atrás dela: o `when` de `FinanceNav` escolhe
 * **ou** esta tela **ou** o resto, e é isso que faz valer o "antes de exibir
 * qualquer dado financeiro" do requisito. Uma sobreposição por cima do
 * dashboard não valeria — o conteúdo estaria montado, e um instante de
 * transição já o mostraria.
 *
 * O prompt sobe sozinho ao entrar, e o botão existe para quem cancelou: sem
 * ele, cancelar deixaria a pessoa numa tela sem saída.
 */
@Composable
fun LockScreen(onDesbloqueado: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    var erro by remember { mutableStateOf<String?>(null) }

    val pedir: () -> Unit = {
        if (activity == null) {
            erro = "Não foi possível abrir a autenticação."
        } else {
            autenticar(activity, onDesbloqueado) { erro = it }
        }
    }

    LaunchedEffect(Unit) { pedir() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bloqueado", style = HeadingSm, color = Slush.ink)
        erro?.let { Text(it, style = Body, color = Slush.ink) }
        FilledCta(text = "Desbloquear", onClick = pedir)
    }
}

private fun autenticar(
    activity: FragmentActivity,
    onOk: () -> Unit,
    onErro: (String) -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onErro(errString.toString())
            }
        },
    )
    prompt.authenticate(promptInfo())
}

/**
 * REQ-SEC-003 pede biometria **com fallback para credencial do aparelho** —
 * sem o fallback, quem não cadastrou digital fica trancado para fora dos
 * próprios dados.
 *
 * ponytail: dois caminhos para a mesma intenção porque `setAllowedAuthenticators`
 * com `DEVICE_CREDENTIAL` só é confiável a partir da API 30 no
 * `androidx.biometric` 1.1.0, e o `minSdk` é 26. O `setDeviceCredentialAllowed`
 * está depreciado e é exatamente o que a documentação manda usar abaixo disso.
 * Cai fora no dia em que o catálogo subir a biblioteca — não antes de rodar nos
 * dois lados, que é onde este tipo de coisa quebra.
 */
@Suppress("DEPRECATION")
private fun promptInfo(): BiometricPrompt.PromptInfo {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear")
        .setSubtitle("Use a biometria ou a senha do aparelho")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        info.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
    } else {
        info.setDeviceCredentialAllowed(true)
    }
    return info.build()
}
