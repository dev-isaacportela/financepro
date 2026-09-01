package app.financepro.data.ingest

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Leitura e farejamento de CSV. REQ-IMP-006 ·
 * [ingestao.md](../../../../../../../../docs/ingestao.md) §1.3
 *
 * **Sem biblioteca** (Art. 10). CSV de banco é simples, e o que uma dependência
 * traria — dialetos, streaming, esquemas — é exatamente o que não se usa aqui.
 * O que dá trabalho no CSV brasileiro não é parsear: é que cada banco escolhe um
 * separador, um formato de data e um decimal diferentes, e às vezes o separador
 * de coluna é o mesmo caractere que o decimal do valor.
 *
 * O farejamento **erra às vezes, e por isso não decide sozinho**: REQ-IMP-005
 * manda a tela de mapeamento oferecer correção manual, com prévia das primeiras
 * linhas. Aqui é o palpite que já vem preenchido, não a palavra final.
 */

/** Os dois formatos de data que aparecem em extrato brasileiro. REQ-IMP-006 */
enum class PadraoDeData(val formato: DateTimeFormatter) {
    DIA_MES_ANO(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
    ANO_MES_DIA(DateTimeFormatter.ofPattern("uuuu-MM-dd")),
}

/**
 * O palpite do farejador. [data] é nula quando nenhuma coluna parecia data — o
 * caso do arquivo que não é extrato, e o que a tela de mapeamento precisa saber
 * para pedir ajuda em vez de mostrar linhas vazias.
 */
data class CsvFormato(
    val separador: Char,
    val decimal: Char,
    val data: PadraoDeData?,
    /**
     * Primeira linha é cabeçalho.
     *
     * Não está em REQ-IMP-006 e entra assim mesmo: sem isso, "Data;Descrição;
     * Valor" viraria uma transação com valor nulo na tela de revisão, e a
     * primeira coisa que o usuário veria da importação seria uma linha errada.
     */
    val temCabecalho: Boolean,
)

/**
 * Linhas e campos, respeitando aspas. RFC 4180 no que interessa: aspa dupla
 * dentro de campo entre aspas, separador e quebra de linha dentro das aspas.
 *
 * Campo é aparado nas pontas. Extrato de banco não usa espaço à esquerda como
 * informação, e um `" 45,90"` sem apara viraria valor inválido no `parseCents`.
 *
 * Linha inteiramente vazia é descartada — arquivo de banco termina com quebra de
 * linha, e sem isto toda importação teria uma transação fantasma no fim.
 */
fun lerCsv(texto: String, separador: Char): List<List<String>> =
    LeitorCsv(separador).ler(texto.removePrefix(BOM))

/**
 * O varredor, caractere a caractere.
 *
 * Classe, e não um `while` com quatro variáveis mutáveis dentro de [lerCsv]: são
 * quatro estados que mudam juntos, e a função passou do limite de complexidade
 * antes mesmo de ficar difícil de ler. Mesmo motivo do `Coletor` do OFX.
 */
private class LeitorCsv(private val separador: Char) {

    private val linhas = mutableListOf<List<String>>()
    private var campos = mutableListOf<String>()
    private val atual = StringBuilder()
    private var entreAspas = false

    fun ler(texto: String): List<List<String>> {
        var i = 0
        while (i < texto.length) i += consumir(texto[i], texto.getOrNull(i + 1))
        fecharLinha()
        return linhas.toList()
    }

    /** Devolve quantos caracteres foram consumidos: 2 no par de aspas e no CRLF. */
    private fun consumir(c: Char, proximo: Char?): Int = when {
        // Aspa dobrada dentro de campo entre aspas é uma aspa literal.
        entreAspas && c == ASPA && proximo == ASPA -> {
            atual.append(ASPA)
            2
        }
        c == ASPA -> {
            entreAspas = !entreAspas
            1
        }
        !entreAspas && c == separador -> {
            fecharCampo()
            1
        }
        !entreAspas && c == CR && proximo == LF -> {
            fecharLinha()
            2
        }
        !entreAspas && (c == LF || c == CR) -> {
            fecharLinha()
            1
        }
        else -> {
            atual.append(c)
            1
        }
    }

    private fun fecharCampo() {
        campos.add(atual.toString().trim())
        atual.clear()
    }

    private fun fecharLinha() {
        fecharCampo()
        if (campos.any { it.isNotEmpty() }) linhas.add(campos.toList())
        campos = mutableListOf()
    }
}

/**
 * Separador, decimal e formato de data, a partir das primeiras linhas.
 * REQ-IMP-006
 */
fun farejarCsv(texto: String): CsvFormato {
    val amostra = texto.lineSequence().filter { it.isNotBlank() }.take(AMOSTRA).joinToString("\n")
    val separador = SEPARADORES.maxByOrNull { pontuacao(amostra, it) } ?: PONTO_E_VIRGULA
    val tabela = lerCsv(amostra, separador)
    val campos = tabela.flatten()

    return CsvFormato(
        separador = separador,
        decimal = decimalDe(campos),
        data = campos.firstNotNullOfOrNull(::padraoDe),
        temCabecalho = tabela.firstOrNull()?.none { padraoDe(it) != null } ?: false,
    )
}

/** A data de uma célula, no padrão que o farejador achou. */
fun parseDataCsv(texto: String, padrao: PadraoDeData?): LocalDate? {
    val formato = padrao?.formato ?: return null
    return runCatching { LocalDate.parse(texto.trim(), formato) }.getOrNull()
}

/**
 * Quanto um separador "explica" o arquivo: colunas × linhas que concordam.
 *
 * As duas metades importam. Só o número de colunas escolheria a vírgula num
 * arquivo `;` cheio de decimais; só a concordância escolheria qualquer caractere
 * ausente, que dá uma coluna em toda linha. Multiplicando, o separador de
 * verdade ganha — e um caractere que nunca aparece pontua zero, porque uma
 * coluna só não explica nada.
 */
private fun pontuacao(amostra: String, separador: Char): Int {
    val contagens = lerCsv(amostra, separador).map { it.size }
    val moda = contagens.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return 0
    return if (moda.key <= 1) 0 else moda.key * moda.value
}

/**
 * O decimal do arquivo, pelas duas últimas casas dos campos numéricos.
 *
 * `parseCents` não precisa deste palpite — ele decide sozinho pelo último
 * separador (REQ-IMP-004). Quem precisa é a tela de mapeamento, que exibe o que
 * entendeu do arquivo para o usuário confirmar (REQ-IMP-005).
 */
private fun decimalDe(campos: List<String>): Char {
    val virgula = campos.count { DECIMAL_VIRGULA.containsMatchIn(it) }
    val ponto = campos.count { DECIMAL_PONTO.containsMatchIn(it) }
    return if (ponto > virgula) '.' else ','
}

private fun padraoDe(campo: String): PadraoDeData? = when {
    DIA_PRIMEIRO.matches(campo) -> PadraoDeData.DIA_MES_ANO
    ANO_PRIMEIRO.matches(campo) -> PadraoDeData.ANO_MES_DIA
    else -> null
}

private const val ASPA = '"'
private const val CR = '\r'
private const val LF = '\n'
private const val PONTO_E_VIRGULA = ';'
private val SEPARADORES = listOf(PONTO_E_VIRGULA, ',', '\t')

/** Dez linhas bastam para o palpite, e mantêm o farejamento barato num arquivo grande. */
private const val AMOSTRA = 10

/** O mesmo BOM que a exportação escreve, e que o Excel devolve no arquivo salvo. */
private const val BOM = "﻿"

private val DECIMAL_VIRGULA = Regex("""\d,\d{2}(\D|$)""")
private val DECIMAL_PONTO = Regex("""\d\.\d{2}(\D|$)""")
private val DIA_PRIMEIRO = Regex("""\d{2}/\d{2}/\d{4}""")
private val ANO_PRIMEIRO = Regex("""\d{4}-\d{2}-\d{2}""")
