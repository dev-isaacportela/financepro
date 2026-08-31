package app.financepro.core.money

/**
 * Dinheiro como o leitor de tela deve ouvi-lo. REQ-A11Y-006 · Art. 17
 *
 * `−R$ 18,50` é ótimo para o olho e péssimo para o ouvido: o TalkBack lê o
 * U+2212 como "traço" ou o ignora, e `R$` vira "erre cifrão". Quem depende do
 * leitor recebe "traço erre cifrão dezoito vírgula cinquenta" e precisa
 * remontar sozinho se aquilo entrou ou saiu da conta.
 *
 * **A soletração é escrita à mão porque não há de onde tirá-la.** O
 * `RuleBasedNumberFormat` do ICU faz exatamente isto e o Android não o expõe:
 * `android.icu.text` publica `NumberFormat` sem o estilo `SPELLOUT`. A
 * alternativa seria arrastar o `com.ibm.icu:icu4j` inteiro — dezenas de MB de
 * dados de locale — para uma função de sessenta linhas.
 *
 * Em troca, o pacote continua Kotlin puro e o teste roda em JVM, em
 * milissegundos, como o resto de `core/money`.
 *
 * Irmão de [formatBRL]: as duas respondem "como este valor se apresenta", uma
 * para o olho e outra para o ouvido, e é por isso que moram juntas.
 */

private const val CENTS_PER_UNIT = 100L
private const val GRUPO = 1000
private const val CEM = 100
private const val DEZ = 10

private val UNIDADES = listOf(
    "zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
    "dez", "onze", "doze", "treze", "catorze", "quinze", "dezesseis", "dezessete",
    "dezoito", "dezenove",
)

private val DEZENAS = listOf(
    "", "", "vinte", "trinta", "quarenta", "cinquenta",
    "sessenta", "setenta", "oitenta", "noventa",
)

private val CENTENAS = listOf(
    "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
    "seiscentos", "setecentos", "oitocentos", "novecentos",
)

/** Singular e plural por potência de mil. O índice é o número do grupo. */
private val ESCALAS = listOf(
    "" to "",
    "mil" to "mil",
    "milhão" to "milhões",
    "bilhão" to "bilhões",
    "trilhão" to "trilhões",
    "quatrilhão" to "quatrilhões",
    "quintilhão" to "quintilhões",
)

/**
 * REQ-A11Y-006 — "menos dezoito reais e cinquenta centavos".
 *
 * | Centavos | Fala                                                             |
 * |----------|------------------------------------------------------------------|
 * | `-1850`  | menos dezoito reais e cinquenta centavos                          |
 * | `100`    | um real                                                           |
 * | `1`      | um centavo                                                        |
 * | `0`      | zero real                                                         |
 * | `10000`  | cem reais                                                         |
 * | `123456` | mil duzentos e trinta e quatro reais e cinquenta e seis centavos  |
 *
 * Zero diz a unidade — "zero real", não "zero": saldo zerado é informação, e é
 * a unidade que o distingue de um campo que o leitor pulou. No singular, que é
 * a concordância do português com zero.
 *
 * As duas partes somem quando são zero, e por simetria: "cem reais e zero
 * centavos" e "zero reais e um centavo" são as duas coisas que ninguém fala em
 * voz alta. Só o valor inteiramente zerado mantém a parte de reais, porque
 * senão não sobraria frase nenhuma.
 *
 * O módulo é tirado **depois** de dividir: `Math.abs(cents)` estouraria em
 * `Long.MIN_VALUE`, enquanto `cents / 100` e `cents % 100` já saíram da borda.
 * E o sinal vira palavra uma vez só, na frente — dizê-lo nos reais e de novo
 * nos centavos soaria como dois valores negativos.
 */
fun spokenBRL(cents: Long): String {
    val negativo = cents < 0
    val reais = Math.abs(cents / CENTS_PER_UNIT)
    val centavos = Math.abs(cents % CENTS_PER_UNIT)

    val partes = buildList {
        // A parte de reais some quando é zero E há centavos: `R$ 0,01` se fala
        // "um centavo", não "zero reais e um centavo". Some só aí — zerado de
        // verdade precisa dizer "zero real".
        if (reais > 0 || centavos == 0L) {
            add(porExtenso(reais) + " " + if (reais <= 1L) "real" else "reais")
        }
        if (centavos > 0) {
            add(porExtenso(centavos) + " " + if (centavos == 1L) "centavo" else "centavos")
        }
    }
    val corpo = partes.joinToString(" e ")
    return if (negativo) "menos $corpo" else corpo
}

/**
 * Um inteiro não negativo por extenso.
 *
 * Quebra em grupos de três e nomeia cada um pela sua escala. O `um` some antes
 * de `mil` — "mil e vinte", nunca "um mil e vinte" — mas fica em `um milhão`,
 * que é como se fala.
 *
 * A junção do último grupo é a única regra sutil do português aqui: entra `e`
 * quando ele é menor que cem ou uma centena redonda ("mil e vinte", "mil e
 * cem"), e só espaço no resto ("mil duzentos e trinta e quatro"). Trocar isso
 * por um `e` sempre produziria "mil e duzentos e trinta e quatro".
 *
 * ponytail: vai até quintilhões, que é onde `Long` de centavos termina. Nenhum
 * teto novo a vigiar — o tipo é o teto.
 */
private fun porExtenso(valor: Long): String {
    val grupos = mutableListOf<Int>()
    var resto = valor
    while (resto > 0) {
        grupos += (resto % GRUPO).toInt()
        resto /= GRUPO
    }

    val partes = grupos.indices.reversed().mapNotNull { i ->
        val g = grupos[i]
        when {
            g == 0 -> null
            i == 0 -> ateNovecentos(g)
            i == 1 && g == 1 -> ESCALAS[i].first
            else -> ateNovecentos(g) + " " + if (g == 1) ESCALAS[i].first else ESCALAS[i].second
        }
    }

    return when {
        // Zero não entra no laço, então não produz grupo nenhum. Tratar aqui e
        // não numa guarda no topo mantém a função com uma saída só.
        partes.isEmpty() -> UNIDADES[0]
        partes.size == 1 -> partes.first()
        else -> {
            val ultimo = grupos.first { it != 0 }
            val junta = if (ultimo < CEM || ultimo % CEM == 0) " e " else " "
            partes.dropLast(1).joinToString(" ") + junta + partes.last()
        }
    }
}

/** 1..999. `cem` só existe exato; a partir de 101 é `cento`. */
private fun ateNovecentos(n: Int): String = when {
    n == CEM -> "cem"
    n > CEM -> CENTENAS[n / CEM] + juntar(n % CEM)
    n >= UNIDADES.size -> DEZENAS[n / DEZ] + juntar(n % DEZ)
    else -> UNIDADES[n]
}

private fun juntar(resto: Int): String = if (resto == 0) "" else " e " + ateNovecentos(resto)
