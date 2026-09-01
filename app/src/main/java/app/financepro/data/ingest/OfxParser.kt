package app.financepro.data.ingest

import app.financepro.core.money.parseCents
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parser de OFX. REQ-IMP-002 · REQ-IMP-003 ·
 * [ingestao.md](../../../../../../../../docs/ingestao.md) §1.2
 *
 * **Um caminho de código para as duas versões.** OFX 1.x é SGML — tags de folha
 * sem fechamento, cabeçalho de chave-valor antes do corpo — e OFX 2.x é XML bem
 * formado. A diferença some quando se lê o arquivo como "uma tag, e o texto até
 * a próxima tag": `<TRNAMT>-187.50` e `<TRNAMT>-187.50</TRNAMT>` dão o mesmo
 * par. Um parser de XML de verdade recusaria o 1.x, que é o que a maioria dos
 * bancos brasileiros ainda exporta.
 *
 * **Sem biblioteca.** Não existe biblioteca de OFX em Kotlin/Java mantida que
 * pague a dependência, e o subconjunto que importa — `STMTTRN` dentro de
 * `STMTRS` ou `CCSTMTRS` — cabe nesta página.
 *
 * O parser **não decide nada de dinheiro**: devolve o que estava no arquivo, e
 * quem transforma em transação, deduplica e categoriza são as T-039 a T-041. Um
 * parser que já filtrasse duplicata teria de conhecer o banco, e é o oposto de
 * ser testável com um arquivo em mãos.
 */

/** Uma linha de extrato, como o arquivo a traz. */
data class OfxTxn(
    val date: LocalDate,
    val amountCents: Long,
    val description: String,
    /** Id do banco, quando existe. É o dedupe perfeito (ingestao.md §3). */
    val fitid: String? = null,
)

/**
 * Um extrato do arquivo. Um OFX pode trazer vários — conta corrente e cartão no
 * mesmo download —, e [acctId] é o que permite importar só o escolhido
 * (REQ-IMP-002, ingestao.md §1.2).
 */
data class OfxStatement(val acctId: String?, val txns: List<OfxTxn>)

/**
 * Lê o arquivo inteiro. Devolve lista vazia quando não há nada reconhecível —
 * arquivo trocado por engano é caso de tela, não de exceção.
 *
 * Recebe **bytes**, e não texto: o charset está declarado dentro do arquivo
 * (REQ-IMP-003), e quem decodifica antes de ler o cabeçalho já perdeu o acento.
 */
fun parseOfx(bytes: ByteArray): List<OfxStatement> {
    val texto = String(bytes, charsetDe(bytes))
    val coletor = Coletor()
    TAG.findAll(texto).forEach { coletor.tag(it.groupValues[1], it.groupValues[2]) }
    return coletor.fechar()
}

/**
 * O charset declarado no arquivo. REQ-IMP-003
 *
 * O cabeçalho é sempre ASCII nas duas versões, então lê-lo como ISO-8859-1 é
 * seguro: aquele charset mapeia todo byte para um caractere, nunca falha, e as
 * chaves que interessam (`ENCODING`, `CHARSET`, `encoding=`) são ASCII puro.
 *
 * `CHARSET:1252` com "ALIMENTAÇÃO" é o caso que a spec cobra: lido como UTF-8, o
 * `Ç` vira o caractere de substituição e a descrição chega quebrada ao dedupe —
 * que então trata a mesma loja como duas.
 */
private fun charsetDe(bytes: ByteArray): Charset {
    val cabecalho = String(bytes.copyOf(minOf(bytes.size, CABECALHO)), Charsets.ISO_8859_1)
    val doXml = XML_ENCODING.find(cabecalho)?.groupValues?.get(1)
    val doOfx = OFX_CHARSET.find(cabecalho)?.groupValues?.get(1)

    return when {
        doXml != null -> charsetPorNome(doXml)
        // `ENCODING:UTF-8` ganha do `CHARSET:` quando os dois aparecem: o
        // segundo é herança do OFX 1.0 e alguns exportadores o deixam em
        // `NONE` mesmo emitindo UTF-8.
        OFX_ENCODING_UTF8.containsMatchIn(cabecalho) -> Charsets.UTF_8
        doOfx != null -> charsetPorNome(doOfx)
        else -> Charsets.UTF_8
    }
}

/**
 * `1252` é o apelido que o cabeçalho do OFX 1.x usa, e não é nome de charset em
 * lugar nenhum. Nome desconhecido cai em ISO-8859-1, e não em UTF-8: um byte
 * alto isolado é inválido em UTF-8 e viraria `?`, enquanto em ISO-8859-1 vira
 * uma letra acentuada possivelmente errada — legível, e corrigível pelo usuário.
 */
private fun charsetPorNome(nome: String): Charset {
    val limpo = nome.trim().trim('"').uppercase()
    if (limpo == "1252" || limpo == "WINDOWS-1252" || limpo == "CP1252") {
        return runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
    }
    return runCatching { Charset.forName(limpo) }.getOrDefault(Charsets.ISO_8859_1)
}

/**
 * A máquina de estados do arquivo.
 *
 * Classe, e não um punhado de `var` dentro de [parseOfx]: são cinco estados que
 * mudam juntos, e uma função de sessenta linhas com cinco variáveis mutáveis é
 * onde se esquece de zerar uma delas entre dois extratos.
 *
 * Tolerante de propósito: `<STMTTRN>` novo fecha o anterior mesmo sem
 * `</STMTTRN>`, e transação que aparece fora de um `<STMTRS>` abre um extrato
 * implícito. Exportador que erra o fechamento é comum, e recusar o arquivo
 * inteiro por isso é pior do que ler o que dá.
 */
private class Coletor {

    private val extratos = mutableListOf<OfxStatement>()
    private var acctId: String? = null
    private var txns = mutableListOf<OfxTxn>()
    private var atual: MutableMap<String, String>? = null
    private var aberto = false

    fun tag(nome: String, texto: String) {
        val chave = nome.uppercase()
        val valor = decodificar(texto.trim())
        when (chave) {
            "STMTRS", "CCSTMTRS" -> abrirExtrato()
            "/STMTRS", "/CCSTMTRS" -> fecharExtrato()
            "STMTTRN" -> abrirTxn()
            "/STMTTRN" -> fecharTxn()
            "ACCTID" -> if (acctId == null) acctId = valor
            else -> if (valor.isNotEmpty()) atual?.put(chave, valor)
        }
    }

    fun fechar(): List<OfxStatement> {
        fecharExtrato()
        return extratos.toList()
    }

    private fun abrirExtrato() {
        fecharExtrato()
        aberto = true
    }

    private fun fecharExtrato() {
        fecharTxn()
        if (aberto || txns.isNotEmpty()) extratos += OfxStatement(acctId, txns.toList())
        aberto = false
        acctId = null
        txns = mutableListOf()
    }

    private fun abrirTxn() {
        fecharTxn()
        aberto = true
        atual = mutableMapOf()
    }

    /**
     * Linha sem valor ou sem data não vira transação, e também não derruba o
     * arquivo: o resto do extrato continua valendo. É o mesmo princípio da
     * tolerância de fechamento.
     */
    private fun fecharTxn() {
        val campos = atual ?: return
        atual = null
        val cents = campos["TRNAMT"]?.let { parseCents(it) }
        val data = campos["DTPOSTED"]?.let(::dataDe)
        if (cents != null && data != null) {
            txns += OfxTxn(
                date = data,
                amountCents = cents,
                // NAME quando existir, senão MEMO (ingestao.md §1.2). O tipo sai
                // do **sinal** de TRNAMT, e TRNTYPE é ignorado (REQ-IMP-002): há
                // banco que manda DEBIT com valor positivo.
                description = campos["NAME"] ?: campos["MEMO"].orEmpty(),
                fitid = campos["FITID"],
            )
        }
    }
}

/**
 * `20260815120000[-3:GMT]` vira `2026-08-15`.
 *
 * Só os oito primeiros dígitos: hora e fuso do OFX descrevem o momento do
 * processamento no banco, não o dia da compra, e usá-los faria uma compra da
 * meia-noite mudar de dia conforme o fuso do arquivo.
 */
private fun dataDe(bruto: String): LocalDate? {
    val digitos = bruto.filter { it.isDigit() }
    if (digitos.length < DIGITOS_DA_DATA) return null
    return runCatching { LocalDate.parse(digitos.take(DIGITOS_DA_DATA), AAAAMMDD) }.getOrNull()
}

/** As cinco entidades do XML, e as numéricas que alguns exportadores emitem. */
private fun decodificar(texto: String): String {
    if ('&' !in texto) return texto
    return texto
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(ENTIDADE_NUMERICA) { it.groupValues[1].toInt().toChar().toString() }
        // `&amp;` por último: antes dos outros, transformaria `&amp;lt;` em `<`.
        .replace("&amp;", "&")
}

/**
 * Uma tag e o texto até a próxima. É o que iguala SGML e XML — ver o KDoc do
 * arquivo. O `/?` no começo captura os fechamentos, que são o que delimita
 * `STMTTRN` e `STMTRS`.
 */
private val TAG = Regex("""<(/?[A-Za-z0-9._]+)>([^<]*)""")

private val XML_ENCODING = Regex("""<\?xml[^>]*encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val OFX_ENCODING_UTF8 = Regex("""ENCODING\s*:\s*UTF-8""", RegexOption.IGNORE_CASE)
private val OFX_CHARSET = Regex("""CHARSET\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
private val ENTIDADE_NUMERICA = Regex("""&#(\d+);""")

/** O cabeçalho das duas versões cabe folgado nisto. */
private const val CABECALHO = 1024
private const val DIGITOS_DA_DATA = 8
private val AAAAMMDD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
