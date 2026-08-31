package com.benenutri.finance.core.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.benenutri.finance.core.testing.Req
import com.benenutri.finance.data.db.CATEGORIAS_PADRAO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * REQ-DS-001 · REQ-DS-004 — os tokens como fonte única, verificados de verdade.
 *
 * Varredura de fonte, não regra de detekt. As regras que fariam isso lá
 * (`ForbiddenMethodCall`) exigem resolução de tipos, que é frágil em projeto
 * Android; e não existe regra genérica de "este padrão não pode aparecer fora
 * deste pacote". É a mesma escolha que `tools/trace.py` já faz para a guarda do
 * Art. 6 — varredura exata, sem falso positivo, em milissegundos.
 *
 * Comentários saem antes da varredura. Sem isso o próprio KDoc que explica a
 * regra reprovaria o build, que foi como o `Money.kt` derrubou o CI uma vez.
 */
@Req("REQ-DS-001", "REQ-DS-004")
class TokenLintTest {

    private val fontes = srcMain().walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `a varredura acha os arquivos, senao ela nao prova nada`() {
        assertTrue("nenhum .kt encontrado em ${srcMain()}", fontes.size > 10)
    }

    @Test
    fun `nenhuma cor literal fora de core-ui-theme`() {
        val infratores = fontes
            .filter { "core/ui/theme" !in it.invariantSeparatorsPath }
            .filter { COR_LITERAL.containsMatchIn(semComentarios(it)) }

        assertEquals(emptyList<String>(), infratores.map { it.name })
    }

    @Test
    fun `nenhuma elevacao diferente de zero`() {
        // O Material 3 traz elevação por padrão em Card, Button, FAB e Surface,
        // e `Surface` ainda tinge o fundo com elevação tonal sem sombra visível.
        // Captura o valor em vez de olhar o que vem depois de `=`: com
        // `\s*(?!0\.dp)` o `\s*` retrocede para zero e o lookahead passa a
        // testar " 0.dp", com espaço — a regra reprovava até quem estava certo.
        val infratores = fontes.flatMap { arquivo ->
            ELEVACAO.findAll(semComentarios(arquivo))
                .map { it.groupValues[1].trim() }
                .filter { it != "0.dp" }
                .map { "${arquivo.name}: $it" }
                .toList()
        }

        assertEquals(emptyList<String>(), infratores)
    }

    @Test
    fun `nenhum gradiente`() {
        val infratores = fontes.filter { GRADIENTE.containsMatchIn(semComentarios(it)) }

        assertEquals(emptyList<String>(), infratores.map { it.name })
    }

    @Test
    fun `as cores das categorias semeadas sao as seis do tema`() {
        // O furo que a varredura de `Color(0x` não pega: `Seed.kt` guarda ARGB
        // como Int porque `data/` não importa Compose, então ele não é uma cor
        // literal no sentido do lint — mas divergiria da paleta em silêncio.
        val doTema = Stickers.map { it.toArgb() }.toSet()
        val doSeed = CATEGORIAS_PADRAO.map { it.corArgb }.toSet()

        assertEquals(emptySet<Int>(), doSeed - doTema)
    }

    private fun semComentarios(arquivo: File): String = arquivo.readText()
        .replace(BLOCO, "")
        .replace(LINHA, "")

    private companion object {
        val COR_LITERAL = Regex("""\bColor\(0x""")
        val ELEVACAO = Regex("""(?:shadow|tonal|default|pressed|focused|hovered)Elevation\s*=\s*([^,)\n]+)""")
        val GRADIENTE = Regex("""\bBrush\.\w*[Gg]radient""")
        val BLOCO = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINHA = Regex("""//[^\n]*""")

        /** Sobe até achar `app/src/main/java`, para o teste não depender do cwd. */
        fun srcMain(): File {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                File(dir, "src/main/java").takeIf { it.isDirectory }?.let { return it }
                File(dir, "app/src/main/java").takeIf { it.isDirectory }?.let { return it }
                dir = dir.parentFile
            }
            error("não achei src/main/java a partir de ${System.getProperty("user.dir")}")
        }
    }
}
