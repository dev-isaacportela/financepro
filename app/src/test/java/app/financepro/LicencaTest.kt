package app.financepro

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * As obrigações de licença, verificadas por máquina.
 *
 * O projeto é Apache 2.0, mas **a fonte empacotada não é**. `inter.ttf` vem sob
 * a SIL Open Font License, que exige que o aviso de copyright e o texto da
 * licença acompanhem toda cópia do arquivo — inclusive o APK, que é onde é mais
 * fácil esquecer, porque lá a fonte não parece um arquivo, parece um glifo.
 *
 * Esta é a classe de erro que só aparece depois de o repositório abrir, e que
 * ninguém encontra revisando diff: o `NOTICE` some num merge, ou a fonte é
 * trocada por outra e o aviso continua falando da anterior. Nos dois casos a
 * distribuição passa a estar irregular sem que nada quebre.
 *
 * Por isso a guarda é a mesma do Art. 4 — verificação por máquina em vez de
 * disciplina humana. O teste é escrito ao contrário do óbvio: ele **parte do
 * binário** que está em `res/font` e cobra o aviso, e não da lista de avisos
 * cobrando os binários. Uma fonte nova entra e reprova sozinha; uma lista
 * ficaria desatualizada em silêncio, que é exatamente o problema.
 */
class LicencaTest {

    @Test
    fun `o projeto tem licenca, e ela e a Apache 2 com o titular preenchido`() {
        val licenca = File(raiz(), "LICENSE")
        assertTrue("LICENSE não existe na raiz", licenca.isFile)

        val texto = licenca.readText()
        assertTrue("LICENSE não parece a Apache 2.0", "Apache License" in texto)
        assertTrue("LICENSE não tem a versão 2.0", "Version 2.0, January 2004" in texto)

        // O apêndice do texto oficial vem com marcadores para preencher. Deixá-los
        // publica uma licença que não nomeia titular nenhum.
        assertTrue(
            "o apêndice do LICENSE ainda tem os marcadores [yyyy] / [name of copyright owner]",
            "[yyyy]" !in texto && "[name of copyright owner]" !in texto,
        )
    }

    @Test
    fun `toda fonte empacotada leva aviso no NOTICE e o texto da licenca junto`() {
        val fontes = File(raiz(), "app/src/main/res/font")
            .listFiles { arquivo -> arquivo.extension.lowercase() in BINARIOS_DE_FONTE }
            .orEmpty()

        assertTrue("nenhuma fonte encontrada em res/font — o teste não prova nada", fontes.isNotEmpty())

        val notice = File(raiz(), "NOTICE")
        assertTrue("NOTICE não existe na raiz", notice.isFile)
        val avisos = notice.readText()

        val semAviso = fontes.filterNot { avisos.contains(it.name, ignoreCase = true) }
        assertTrue("fonte empacotada sem aviso no NOTICE: ${semAviso.map { it.name }}", semAviso.isEmpty())

        val ofl = File(raiz(), "licenses/Inter-OFL.txt")
        assertTrue("licenses/Inter-OFL.txt não existe", ofl.isFile)
        assertTrue(
            "licenses/Inter-OFL.txt não parece o texto da SIL Open Font License",
            "SIL OPEN FONT LICENSE" in ofl.readText().uppercase(),
        )
    }

    private companion object {
        val BINARIOS_DE_FONTE = setOf("ttf", "otf", "ttc")

        /** Sobe até a raiz do repositório, para o teste não depender do cwd. */
        fun raiz(): File {
            val cwd = requireNotNull(System.getProperty("user.dir")) { "user.dir não definido" }
            var dir: File? = File(cwd).absoluteFile
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").isFile) return dir
                dir = dir.parentFile
            }
            error("não achei a raiz do repositório a partir de $cwd")
        }
    }
}
