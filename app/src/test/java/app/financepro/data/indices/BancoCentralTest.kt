package app.financepro.data.indices

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * REQ-INV-005 — a leitura da série 4389, sem rede.
 *
 * A resposta real do BCB, copiada como vem. O que este teste protege não é o
 * caminho feliz: é o dia em que a série mudar de formato e o app precisar
 * responder com o último valor guardado em vez de um número errado.
 */
@Req("REQ-INV-005")
class BancoCentralTest {

    @Test
    fun `le a resposta da serie 4389`() {
        val cdi = lerCdi("""[{"data":"02/09/2026","valor":"14.90"}]""")

        assertEquals(1490, cdi?.anualBp)
        assertEquals(LocalDate.of(2026, 9, 2), cdi?.em)
        assertEquals(false, cdi?.manual)
    }

    @Test
    fun `duas casas decimais sao pontos-base, sem ponto flutuante`() {
        // É por isso que `parseCents` serve aqui sem adaptação: centésimos de
        // real e centésimos de por cento são a mesma conversão de texto.
        assertEquals(1500, lerCdi("""[{"data":"02/09/2026","valor":"15.00"}]""")?.anualBp)
        assertEquals(1, lerCdi("""[{"data":"02/09/2026","valor":"0.01"}]""")?.anualBp)
    }

    @Test
    fun `usa o ponto mais recente quando vem mais de um`() {
        val corpo = """[{"data":"01/09/2026","valor":"14.65"},{"data":"02/09/2026","valor":"14.90"}]"""

        assertEquals(1490, lerCdi(corpo)?.anualBp)
    }

    @Test
    fun `campo novo na resposta nao quebra a leitura`() {
        // `ignoreUnknownKeys`: o BCB acrescentar uma coluna não pode derrubar
        // o CDI do app.
        val cdi = lerCdi("""[{"data":"02/09/2026","valor":"14.90","serie":"4389"}]""")

        assertEquals(1490, cdi?.anualBp)
    }

    @Test
    fun `resposta vazia, quebrada ou fora do formato devolve nulo`() {
        assertNull(lerCdi("[]"))
        assertNull(lerCdi(""))
        assertNull(lerCdi("<html>manutenção</html>"))
        assertNull(lerCdi("""{"erro":"indisponível"}"""))
        assertNull(lerCdi("""[{"data":"2026-09-02","valor":"14.90"}]"""))
        assertNull(lerCdi("""[{"data":"02/09/2026","valor":"catorze"}]"""))
    }

    @Test
    fun `CDI zerado ou negativo nao vale`() {
        // Zero passaria por "número válido" e viraria rendimento previsto de
        // R$ 0,00 exibido como fato. Nulo mantém o último valor conhecido.
        assertNull(lerCdi("""[{"data":"02/09/2026","valor":"0.00"}]"""))
        assertNull(lerCdi("""[{"data":"02/09/2026","valor":"-1.00"}]"""))
    }
}
