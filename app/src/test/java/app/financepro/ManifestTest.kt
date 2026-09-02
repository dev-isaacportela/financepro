package app.financepro

import android.Manifest
import android.content.pm.PackageManager
import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * REQ-SEC-007 — o app pede exatamente estas permissões, e nenhuma outra.
 *
 * A regra anterior era "`INTERNET` não existe aqui". A T-050 a trouxe, para ler
 * o CDI da série pública do Banco Central (ADR-012), e a garantia que a ausência
 * dela dava precisava de substituta — senão a lista de permissões voltaria a
 * crescer sem ninguém olhar, que é como ela sempre cresce.
 *
 * A substituta é **mais forte que a regra que ela troca**: em vez de proibir
 * três permissões pelo nome, o teste exige o conjunto exato. Qualquer permissão
 * nova reprova o build — inclusive, e principalmente, a que entra por
 * dependência transitiva, no manifesto de um AAR que ninguém abriu. Foi assim
 * que as quatro do WorkManager apareceram, e é por isso que elas estão
 * nomeadas abaixo em vez de terem entrado caladas.
 *
 * Lê o manifesto **mesclado**, via `PackageManager`, e não o do módulo.
 *
 * A outra metade da guarda está em `tools/trace.py`: `INTERNET` no manifesto
 * permite falar com a rede, e é a varredura de URL no fonte que decide **com
 * quem**. Uma sem a outra não garante nada.
 */
@Req("REQ-SEC-007", "REQ-DS-010", "REQ-IMP-001", "REQ-INV-005")
@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun `manifesto mesclado declara exatamente estas permissoes`() {
        // Cada linha diz quem a pediu e para quê. Uma permissão a mais neste
        // conjunto é uma decisão de produto, não um detalhe de dependência —
        // e este teste é o lugar onde ela precisa ser escrita à mão para passar.
        val esperadas = setOf(
            // REQ-SEC-003 — bloqueio biométrico, declarado no nosso manifesto.
            Manifest.permission.USE_BIOMETRIC,
            // Herdada da androidx.biometric, para aparelho anterior ao Android 9.
            "android.permission.USE_FINGERPRINT",
            // REQ-INV-005 — o CDI da série pública do BCB, e nada mais.
            Manifest.permission.INTERNET,
            // As quatro abaixo vêm do WorkManager, que agenda a busca diária.
            // Nenhuma delas foi digitada por nós; todas entraram com a
            // dependência, e é exatamente por isso que estão listadas.
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
            // Também do WorkManager, e a única que não é do sistema: uma
            // permissão do próprio app, com nível `signature`, para os
            // receivers que ele registra em tempo de execução. Não pede nada
            // ao usuário e não aparece na loja — mas aparece aqui, que é o
            // ponto de o teste ler a lista inteira em vez de uma allowlist de
            // prefixo "android.permission".
            "app.financepro.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )

        val permissoes = declaradas()

        assertEquals(
            "a lista de permissões do app mudou — se foi de propósito, atualize " +
                "REQ-SEC-007 e o ADR-012 no mesmo commit",
            esperadas,
            permissoes.toSet(),
        )
    }

    @Test
    fun `manifesto mesclado nao declara leitura de armazenamento`() {
        // REQ-IMP-001 — a importação escolhe arquivo por `ACTION_OPEN_DOCUMENT`,
        // e o seletor do sistema entrega um `Uri` já autorizado. Pedir
        // `READ_EXTERNAL_STORAGE` seria pedir a pasta inteira do usuário para
        // ler um arquivo que ele acabou de apontar — e a permissão entra tão
        // fácil por dependência transitiva quanto a INTERNET acima.
        val permissoes = declaradas()

        val armazenamento = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        )

        assertFalse(
            "alguém pediu leitura de armazenamento: $permissoes",
            permissoes.any { it in armazenamento },
        )
    }

    private fun declaradas(): List<String> = app.packageManager
        .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions
        ?.toList()
        .orEmpty()

    @Test
    fun `as fontes estao empacotadas, sem Downloadable Fonts`() {
        // REQ-DS-010. Downloadable Fonts exige rede e Google Play Services, e
        // furaria REQ-SEC-007 pela porta dos fundos — sem `<uses-permission>`
        // nenhum aparecer no manifesto, que é onde o teste acima vigia.
        val fontes = File("src/main/res/font").listFiles().orEmpty()

        assertTrue("res/font vazio", fontes.any { it.extension == "ttf" || it.extension == "otf" })
        assertEquals(
            "fonte declarada por provider é Downloadable Font",
            emptyList<String>(),
            fontes.filter { "fontProviderAuthority" in it.readTextIfXml() }.map { it.name },
        )
    }

    private fun File.readTextIfXml() = if (extension == "xml") readText() else ""


    @Test
    fun `o manifesto que o teste le e mesmo o mesclado`() {
        // Sem esta âncora o teste acima passaria por vacuidade: um manifesto
        // que não chegou ao teste também não declara INTERNET.
        val atividades = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
            .activities
            ?.map { it.name }
            .orEmpty()

        assertTrue(
            "manifesto mesclado não chegou ao teste: $atividades",
            MainActivity::class.qualifiedName in atividades,
        )
    }
}
