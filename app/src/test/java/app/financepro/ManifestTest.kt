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
 * REQ-SEC-007 — o app não pede rede nas fases F0 a F3.
 *
 * Um app financeiro sem permissão de `INTERNET` é uma garantia que o usuário
 * confere sozinho nas informações do app, e não uma promessa na política de
 * privacidade. Este teste é o que impede a garantia de morrer sem ninguém
 * perceber.
 *
 * Lê o manifesto **mesclado**, via `PackageManager`, e não o do módulo: a
 * permissão quase nunca entra por alguém digitando `<uses-permission>` — entra
 * por dependência transitiva, no manifesto de um AAR que ninguém abriu.
 *
 * A partir da F4 a T-049 adiciona `INTERNET` de propósito, e é **aqui** que a
 * regra muda junto, de preferência no mesmo commit.
 */
@Req("REQ-SEC-007", "REQ-DS-010")
@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun `manifesto mesclado nao declara INTERNET`() {
        val permissoes = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()

        assertFalse(
            "alguém — ou alguma dependência — trouxe INTERNET de volta: $permissoes",
            Manifest.permission.INTERNET in permissoes,
        )
    }

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
