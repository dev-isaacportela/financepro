package app.financepro.data.ingest

import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate

/**
 * Normalização de descrição e chave de deduplicação.
 * REQ-IMP-008 · REQ-ACT-004 · [ingestao.md](../../../../../../../../docs/ingestao.md) §3
 *
 * **Uma função só, e é esse o requisito.** REQ-ACT-004 existe porque duas
 * normalizações divergentes fariam o dedupe e o aprendizado de estabelecimento
 * discordarem sobre o que é a mesma padaria: a importação descartaria como
 * duplicata o que o aprendizado trata como loja nova, ou o contrário.
 *
 * O pacote é `ingest` e não `import` como a arquitetura escrevia: `import` é
 * palavra reservada em Kotlin, e um pacote com esse nome só se referencia com
 * crase em todo arquivo que o usa.
 */

/**
 * Descrição de extrato reduzida ao que identifica o estabelecimento. REQ-IMP-008
 *
 * A ordem das etapas é o que faz a tabela do requisito fechar:
 *
 * | Entrada | Saída |
 * |---|---|
 * | `Supermercado Xyz` | `SUPERMERCADO XYZ` |
 * | `PADARIA 00123456` | `PADARIA` |
 * | `UBER   TRIP` | `UBER TRIP` |
 *
 * **Quatro dígitos ou mais** somem porque é o que distingue duas passagens pela
 * mesma padaria sem ser o estabelecimento: NSU, número de autorização, final do
 * cartão. Três ou menos ficam, senão `POSTO 24H` e `POSTO 12H` virariam a mesma
 * chave — e lojas com número no nome são comuns.
 *
 * Acentos saem por decomposição NFD e remoção das marcas, não por uma tabela de
 * pares: a tabela esqueceria o Ç na primeira revisão, e "AÇOUGUE" com e sem
 * cedilha viraria dois estabelecimentos.
 */
fun normalize(descricao: String): String = Normalizer
    .normalize(descricao.uppercase(), Normalizer.Form.NFD)
    .replace(MARCAS, "")
    .replace(SEQUENCIA_LONGA, " ")
    .replace(FORA_DO_ALFABETO, " ")
    .replace(ESPACOS, " ")
    .trim()

/**
 * A chave que descarta duplicata exata quando não há `FITID`. REQ-IMP-008
 *
 * Conta, dia, centavos e descrição normalizada. Os quatro juntos, porque
 * nenhum sozinho basta: o mesmo valor no mesmo dia em contas diferentes são
 * duas transações, e duas compras de R$ 20 na mesma padaria no mesmo dia são
 * duas compras — mas o extrato reimportado traz exatamente as mesmas quatro
 * coisas, e é isso que o hash pega.
 *
 * O prefixo `h:` distingue esta chave da do OFX (`ofx:$fitid`), que é o dedupe
 * perfeito quando o banco fornece um id. Sem o prefixo, um `FITID` que por acaso
 * parecesse um hash colidiria com uma linha de CSV.
 */
fun dedupeKey(accountId: Long, data: LocalDate, cents: Long, descricao: String): String {
    val material = "$accountId|${data.toEpochDay()}|$cents|${normalize(descricao)}"
    val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
    return PREFIXO_HASH + digest.joinToString("") { "%02x".format(it) }
}

private const val PREFIXO_HASH = "h:"

/** As marcas de acento que a decomposição NFD separa das letras. */
private val MARCAS = Regex("""\p{M}""")

/** NSU, autorização, final de cartão. Ver o KDoc de [normalize]. */
private val SEQUENCIA_LONGA = Regex("""\d{4,}""")

private val FORA_DO_ALFABETO = Regex("""[^A-Z0-9 ]""")
private val ESPACOS = Regex("""\s+""")
