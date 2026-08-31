package app.financepro.core.money

/**
 * Dinheiro em centavos.
 *
 * REQ-CORE-001 · REQ-CORE-004 · REQ-CORE-005 · REQ-IMP-004
 * Constituição Art. 6 e Art. 7 · [ADR-002](../../../../../../../../docs/decisoes.md)
 *
 * Nenhuma função deste arquivo usa `Double`, `Float` ou `BigDecimal`, e isso não
 * é estilo: `0.1 + 0.2 != 0.3` em ponto flutuante binário, e num app de finanças
 * isso vira saldo que não fecha com o extrato do banco.
 *
 * O vazamento real não acontece no cálculo, acontece no **parser**:
 * `"187.50".toDouble()` na importação de OFX/CSV. Por isso [parseCents] converte
 * por manipulação de texto, e é a mesma função que a F2 usa — não existe uma
 * segunda conversão texto→centavos no projeto.
 */

private const val CURRENCY = "R\$"
private const val GROUP_SIZE = 3
private const val FRACTION_DIGITS = 2

/** Sinal de menos tipográfico (U+2212), não hífen. REQ-CORE-005. */
private const val MINUS = "−"

private val SEPARATORS = charArrayOf('.', ',')

/**
 * Espaço não separável (U+00A0) e espaço estreito (U+202F) aparecem em CSV
 * exportado de páginas web. `Char.isWhitespace()` devolve `false` para os dois,
 * então precisam ser listados.
 */
private val BLANKS = setOf(' ', ' ')

/** Hífen e o menos tipográfico que o próprio [formatBRL] emite. */
private val NEGATIVE_SIGNS = setOf('-', '−')

/**
 * Converte texto monetário em centavos, ou `null` se a entrada não for um valor.
 *
 * Aceita os dois formatos que circulam em extrato brasileiro, porque os
 * exportadores divergem entre si:
 *
 * | Entrada      | Centavos |
 * |--------------|----------|
 * | `-187.50`    | `-18750` |
 * | `-187,50`    | `-18750` |
 * | `1.234,56`   | `123456` |
 * | `1,234.56`   | `123456` |
 * | `100`        | `10000`  |
 * | `0.07`       | `7`      |
 *
 * A regra que desfaz a ambiguidade: **o último separador é o decimal quando
 * vem seguido de 1 ou 2 dígitos**; caso contrário todos os separadores são de
 * milhar. Assim `1.234` são mil duzentos e trinta e quatro reais, não um real
 * e vinte e três centavos — que é a leitura correta em pt-BR.
 *
 * Limite conhecido: entrada com 3 casas decimais (`0.070`) é lida como milhar.
 * É consequência direta da regra acima e não afeta BRL, que tem 2 casas.
 */
@Suppress("ReturnCount")
fun parseCents(input: String): Long? {
    val cleaned = input
        .replace(CURRENCY, "", ignoreCase = true)
        .filterNot { it.isWhitespace() || it in BLANKS }
    if (cleaned.isEmpty()) return null

    val negative = cleaned.first() in NEGATIVE_SIGNS
    val body = if (negative || cleaned.first() == '+') cleaned.drop(1) else cleaned
    if (body.isEmpty()) return null
    if (body.any { !it.isDigit() && it !in SEPARATORS }) return null

    val (wholeText, fractionText) = splitDecimal(body)
    val wholeDigits = wholeText.filter { it.isDigit() }
    if (wholeDigits.isEmpty() && fractionText.isEmpty()) return null

    val whole = wholeDigits.ifEmpty { "0" }
    val fraction = fractionText.padEnd(FRACTION_DIGITS, '0')
    val value = (whole + fraction).toLongOrNull() ?: return null

    return if (negative) -value else value
}

/**
 * Separa o corpo numérico em (parte inteira, parte fracionária).
 *
 * Aqui mora a regra que desfaz a ambiguidade entre `1.234,56` e `1,234.56`:
 * **o último separador é o decimal quando vem seguido de 1 ou 2 dígitos**.
 * Caso contrário todos os separadores são de milhar, e a fração é vazia — que
 * é como `1.234` vira mil duzentos e trinta e quatro reais.
 */
private fun splitDecimal(body: String): Pair<String, String> {
    val lastSeparator = body.lastIndexOfAny(SEPARATORS)
    if (lastSeparator < 0) return body to ""

    val trailingDigits = body.length - lastSeparator - 1
    return if (trailingDigits in 1..FRACTION_DIGITS) {
        body.take(lastSeparator) to body.substring(lastSeparator + 1)
    } else {
        body to ""
    }
}

/**
 * Formata centavos em pt-BR: `R$ 1.234,56`, e despesa com sinal explícito
 * (`−R$ 18,50`). REQ-CORE-005.
 *
 * Formatação manual, não `NumberFormat`: a API de currency do Java recebe
 * `Double` ou `BigDecimal`, e passar dinheiro por qualquer um dos dois é
 * exatamente o que o Art. 6 proíbe. Também evita depender dos dados de ICU,
 * que variam entre versões do Android.
 */
fun formatBRL(cents: Long): String {
    // Sem negação aritmética: `-Long.MIN_VALUE` estoura. O texto já traz os dígitos.
    val digits = cents.toString().removePrefix("-").padStart(FRACTION_DIGITS + 1, '0')
    val whole = digits.dropLast(FRACTION_DIGITS)
    val fraction = digits.takeLast(FRACTION_DIGITS)
    val grouped = whole.reversed().chunked(GROUP_SIZE).joinToString(".").reversed()
    val sign = if (cents < 0) MINUS else ""
    return "$sign$CURRENCY $grouped,$fraction"
}
