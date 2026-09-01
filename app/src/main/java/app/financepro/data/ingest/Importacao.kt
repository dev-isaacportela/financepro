package app.financepro.data.ingest

import app.financepro.core.money.parseCents

/**
 * A cola entre os parsers e o motor de dedupe. REQ-IMP-005 ·
 * [ingestao.md](../../../../../../../../docs/ingestao.md) §1
 *
 * Puro de propósito: recebe bytes ou texto e devolve [Candidata], sem tocar em
 * banco nem em `Uri`. É o que permite provar em JVM as três coisas que erram
 * sozinhas — qual formato é o arquivo, qual coluna é qual, e qual chave de
 * dedupe cada linha ganha.
 */

enum class FormatoDeArquivo { OFX, CSV }

/**
 * OFX ou CSV, pelo começo do arquivo.
 *
 * Pela **assinatura**, não pela extensão: OFX exportado com extensão `.txt` é
 * comum (ingestao.md §1.1 já lista `text/plain` entre os tipos aceitos), e o
 * seletor do sistema devolve `application/octet-stream` para meio mundo.
 */
fun formatoDe(bytes: ByteArray): FormatoDeArquivo {
    val inicio = String(bytes.copyOf(minOf(bytes.size, FAROL)), Charsets.ISO_8859_1).uppercase()
    return if (MARCA_OFX.containsMatchIn(inicio)) FormatoDeArquivo.OFX else FormatoDeArquivo.CSV
}

/**
 * O texto de um CSV, que ao contrário do OFX não declara charset.
 *
 * UTF-8 primeiro, e ISO-8859-1 quando o resultado traz o caractere de
 * substituição — que é exatamente o sinal de que os bytes não eram UTF-8.
 * Extrato de banco brasileiro em ISO-8859-1 ainda é comum, e sem esta volta
 * "ALIMENTAÇÃO" chegaria com losangos no meio.
 */
fun textoDeCsv(bytes: ByteArray): String {
    val utf8 = String(bytes, Charsets.UTF_8)
    return if (SUBSTITUICAO in utf8) String(bytes, Charsets.ISO_8859_1) else utf8
}

/** Quais colunas do CSV são o quê. REQ-IMP-005 */
data class MapeamentoCsv(val data: Int, val valor: Int, val descricao: Int) {
    fun valido(colunas: Int): Boolean =
        listOf(data, valor, descricao).all { it in 0 until colunas } &&
            setOf(data, valor, descricao).size == COLUNAS
}

/**
 * O que identifica o dialeto de um banco, para reusar o mapeamento na próxima
 * importação. REQ-IMP-005
 *
 * A **primeira linha**, e não o nome do arquivo: o extrato baixado em setembro
 * se chama diferente do de agosto, e o cabeçalho do mesmo banco é o mesmo
 * sempre. Arquivo sem cabeçalho cai na primeira linha de dados, que também é
 * estável em quantidade de colunas — e no pior caso o usuário remapeia.
 */
fun assinaturaCsv(tabela: List<List<String>>): String =
    tabela.firstOrNull().orEmpty().joinToString("|") { normalize(it) }

/**
 * As linhas de um extrato OFX viram candidatas. REQ-IMP-007
 *
 * `FITID` vira a chave quando existe — é o dedupe perfeito. Sem ele, cai no
 * hash, igual ao CSV.
 */
fun candidatasDoOfx(extrato: OfxStatement, accountId: Long): List<Candidata> =
    extrato.txns.map { txn ->
        Candidata(
            date = txn.date,
            amountCents = txn.amountCents,
            description = txn.description,
            dedupeKey = txn.fitid?.takeIf { it.isNotBlank() }?.let(::chaveOfx)
                ?: dedupeKey(accountId, txn.date, txn.amountCents, txn.description),
        )
    }

/**
 * As linhas de um CSV viram candidatas, segundo o mapeamento. REQ-IMP-005
 *
 * Linha que não produz data **ou** valor é descartada em silêncio: é o rodapé de
 * saldo, a linha em branco do meio, o "Total do período" que muitos bancos
 * colocam no fim. Derrubar a importação inteira por causa deles seria recusar o
 * arquivo por um problema que não é do usuário.
 */
fun candidatasDoCsv(
    tabela: List<List<String>>,
    formato: CsvFormato,
    mapa: MapeamentoCsv,
    accountId: Long,
): List<Candidata> = tabela
    .drop(if (formato.temCabecalho) 1 else 0)
    .mapNotNull { linha ->
        val data = linha.getOrNull(mapa.data)?.let { parseDataCsv(it, formato.data) }
        val cents = linha.getOrNull(mapa.valor)?.let { parseCents(it) }
        if (data == null || cents == null || cents == 0L) {
            null
        } else {
            val descricao = linha.getOrNull(mapa.descricao).orEmpty()
            Candidata(
                date = data,
                amountCents = cents,
                description = descricao,
                dedupeKey = dedupeKey(accountId, data, cents, descricao),
            )
        }
    }

/**
 * O palpite de mapeamento, para a tela já abrir preenchida. REQ-IMP-005
 *
 * A coluna de **data** é a primeira que vira data; a de **valor** é a primeira
 * numérica depois dela, o que evita a coluna de saldo que quase todo extrato
 * traz logo em seguida; a de **descrição** é a primeira que sobra e não é
 * número. Erra em algum banco, e é por isso que REQ-IMP-005 exige correção
 * manual — o palpite economiza três toques, não decide nada.
 *
 * Nulo quando não há data ou não há valor: aí a tela pergunta do zero.
 */
fun palpiteDeMapeamento(tabela: List<List<String>>, formato: CsvFormato): MapeamentoCsv? {
    val linha = tabela.drop(if (formato.temCabecalho) 1 else 0).firstOrNull().orEmpty()
    val data = linha.indices.firstOrNull { parseDataCsv(linha[it], formato.data) != null }
    val valor = data?.let { d ->
        linha.indices.firstOrNull { it > d && parseCents(linha[it]) != null }
    }
    val descricao = valor?.let { v ->
        linha.indices.firstOrNull { it != data && it != v && parseCents(linha[it]) == null }
    }
    return if (descricao == null) null else MapeamentoCsv(data!!, valor!!, descricao)
}

/** Colunas que o mapeamento nomeia: data, valor e descrição. */
private const val COLUNAS = 3

/** O cabeçalho do OFX cabe folgado nisto, nas duas versões. */
private const val FAROL = 512

private const val SUBSTITUICAO = '�'

private val MARCA_OFX = Regex("""OFXHEADER|<OFX""")
