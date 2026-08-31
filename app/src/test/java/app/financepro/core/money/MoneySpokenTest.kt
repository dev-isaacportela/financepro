package app.financepro.core.money

import app.financepro.core.testing.Req
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * REQ-A11Y-006 — o valor que o leitor de tela fala.
 *
 * JVM pura, sem Robolectric: a soletração é Kotlin, porque o Android não expõe
 * o `SPELLOUT` do ICU e arrastar o `icu4j` inteiro por sessenta linhas seria
 * pior que escrevê-las.
 */
@Req("REQ-A11Y-006")
class MoneySpokenTest {

    @Test
    fun `o exemplo do requisito`() {
        assertEquals("menos dezoito reais e cinquenta centavos", spokenBRL(-1850))
    }

    @Test
    fun `singular e plural`() {
        assertEquals("um real", spokenBRL(100))
        assertEquals("dois reais", spokenBRL(200))
        assertEquals("um centavo", spokenBRL(1))
        assertEquals("dois centavos", spokenBRL(2))
    }

    @Test
    fun `so centavos nao anuncia os reais`() {
        // R$ 0,01 se fala "um centavo". "Zero reais e um centavo" é ruído.
        assertEquals("um centavo", spokenBRL(1))
        assertEquals("cinquenta centavos", spokenBRL(50))
        assertEquals("menos cinquenta centavos", spokenBRL(-50))
    }

    @Test
    fun `zero diz a unidade`() {
        // "zero" sozinho não distingue saldo zerado de campo que o leitor pulou.
        assertEquals("zero real", spokenBRL(0))
    }

    @Test
    fun `centavo zero nao vira palavra`() {
        // Ninguém fala "cem reais e zero centavos" em voz alta.
        assertEquals("cem reais", spokenBRL(10000))
    }

    @Test
    fun `o sinal aparece uma vez so`() {
        val fala = spokenBRL(-1850)
        assertEquals(1, fala.split("menos").size - 1)
    }

    @Test
    fun `nada de digito cru nem simbolo`() {
        // O defeito que este arquivo existe para impedir: "traço erre cifrão
        // dezoito vírgula cinquenta".
        listOf(-1850L, 0L, 1L, 123456L, 100000000L).forEach { cents ->
            val fala = spokenBRL(cents)
            assertFalse(fala, fala.any { it.isDigit() })
            assertFalse(fala, fala.contains("R$"))
            assertFalse(fala, fala.contains("−"))
            assertFalse(fala, fala.contains(","))
        }
    }

    @Test
    fun `valores grandes ainda sao uma frase`() {
        assertEquals(
            "mil duzentos e trinta e quatro reais e cinquenta e seis centavos",
            spokenBRL(123456),
        )
    }

    @Test
    fun `extremos nao estouram`() {
        // `Math.abs(cents)` estouraria aqui; o módulo sai depois da divisão.
        listOf(Long.MIN_VALUE, Long.MAX_VALUE).forEach { cents ->
            val fala = spokenBRL(cents)
            assertFalse(fala, fala.isBlank())
            assertFalse(fala, fala.any { it.isDigit() })
        }
    }
}
