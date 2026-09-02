package app.financepro.data.indices

import app.financepro.core.money.parseCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * O CDI, da série pública do Banco Central. REQ-INV-005 · REQ-SEC-007
 *
 * **É a única chamada de rede do app**, e o que ela pede é um número que vale
 * igual para todo mundo. Nenhum dado do usuário sai daqui: a requisição é um
 * `GET` sem parâmetro, sem cabeçalho de identificação, sem corpo e sem cookie —
 * o servidor do BCB não tem como saber nada além de que alguém perguntou o CDI.
 * `tools/trace.py` reprova o build se aparecer URL para qualquer outro host.
 *
 * Sem cliente HTTP novo: `HttpURLConnection` da biblioteca padrão e o
 * `kotlinx.serialization` que o app já usa nas rotas. Um OkHttp para uma
 * requisição por dia seria uma dependência inteira — com seu próprio pool de
 * conexões e sua própria thread — para o que cabe em vinte linhas.
 *
 * A série 4389 é o CDI **anualizado**, base 252, que é a forma como a taxa é
 * anunciada ("CDI a 14,90%") e a mesma em que o usuário digita o percentual do
 * papel. A série 12 traria a taxa diária, e converter de volta seria uma
 * potência de 252 para chegar onde a 4389 já está.
 */

/**
 * O único host que este app contata. REQ-SEC-007
 *
 * Fica em constante e não interpolado na função porque é o que a varredura de
 * `tools/trace.py` lê — e porque uma URL montada com `+` é como um segundo host
 * entraria sem aparecer em revisão.
 */
const val URL_CDI = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.4389/dados/ultimos/1?formato=json"

private const val TIMEOUT_MS = 10_000

private val Sgs = Json { ignoreUnknownKeys = true }

private val DATA_SGS: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@kotlinx.serialization.Serializable
private data class PontoSgs(val data: String, val valor: String)

/**
 * O CDI mais recente, ou `null` se não deu.
 *
 * **Falha é `null`, não exceção.** Modo avião, servidor fora do ar e resposta
 * em formato novo dão todos o mesmo resultado do ponto de vista de quem chama:
 * continua valendo o último valor guardado. Um `throw` obrigaria o worker e a
 * tela a repetirem o mesmo `try` — e o Art. 15 proíbe log, então não haveria
 * nem onde registrar a diferença entre os três casos.
 *
 * O `valor` vem como `"14.90"`, com duas casas decimais, e [parseCents] o
 * converte em `1490` — que é a taxa em pontos-base. É a mesma função de
 * texto→inteiro que a importação de extrato usa: um `toDouble()` aqui seria o
 * segundo parser do projeto, e o Art. 6 existe porque o primeiro já bastava.
 */
suspend fun buscarCdi(): Cdi? = withContext(Dispatchers.IO) {
    baixar()?.let { lerCdi(it) }
}

/**
 * O corpo da resposta virando [Cdi], ou `null` se ele não for o que se espera.
 *
 * Separado de [buscarCdi] para poder ser testado sem rede — é aqui que mora
 * tudo que pode dar errado sem ser um cabo desligado: a série mudar de formato,
 * o valor vir com vírgula, a data vir em ISO. O download em si não tem regra
 * nenhuma para testar.
 */
@Suppress("ReturnCount")
internal fun lerCdi(corpo: String): Cdi? {
    val ponto = try {
        Sgs.decodeFromString<List<PontoSgs>>(corpo).lastOrNull()
    } catch (_: SerializationException) {
        null
    } ?: return null

    val bp = parseCents(ponto.valor)?.toInt() ?: return null
    if (bp <= 0) return null
    val em = try {
        LocalDate.parse(ponto.data, DATA_SGS)
    } catch (_: DateTimeParseException) {
        return null
    }
    return Cdi(anualBp = bp, em = em, manual = false)
}

private fun baixar(): String? {
    val conexao = try {
        URL(URL_CDI).openConnection() as HttpURLConnection
    } catch (_: IOException) {
        return null
    }
    return try {
        conexao.connectTimeout = TIMEOUT_MS
        conexao.readTimeout = TIMEOUT_MS
        if (conexao.responseCode != HttpURLConnection.HTTP_OK) {
            null
        } else {
            conexao.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (_: IOException) {
        null
    } finally {
        conexao.disconnect()
    }
}
