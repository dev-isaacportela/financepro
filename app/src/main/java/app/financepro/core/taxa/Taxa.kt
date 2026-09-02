package app.financepro.core.taxa

import app.financepro.domain.model.Indexador
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Taxa de rendimento. REQ-INV-001 · REQ-INV-002
 *
 * **Este é o único arquivo do módulo com ponto flutuante, e a escolha do pacote
 * é o que a torna honesta.** Converter taxa anual em mensal é uma raiz
 * décima-segunda — `(1 + i)^(1/12)` — e ela não existe em aritmética inteira.
 * O Art. 6 proíbe `Double` em **caminho de dinheiro**, e `tools/trace.py` marca
 * quatro: `/core/money/`, `/domain/`, `/data/ingest/` e `/data/export/`. Taxa
 * não é dinheiro, e `core/taxa/` fica de fora dos quatro de propósito.
 *
 * A fronteira é literal: nenhum centavo entra ou sai daqui. A saída é um fator
 * em **partes por milhão**, e quem multiplica dinheiro por ele é
 * `rendimentoPrevisto`, no domínio, em `Long`. Um `Double` que atravessasse
 * essa linha seria o erro que o artigo descreve; um que pare antes dela é a
 * matemática que a regra exige.
 *
 * As duas escalas inteiras do módulo:
 * - **pontos-base** (`bp`), centésimos de por cento: `1490` = 14,90% a.a.
 *   É a escala em que o BCB publica o CDI e em que a conta guarda a taxa.
 * - **partes por milhão** (`ppm`): `11640` = 1,1640% a.m. Precisão maior porque
 *   a taxa mensal tem uma casa decimal a mais que a anual, e arredondá-la em
 *   pontos-base custaria meio ponto-base por mês, doze vezes por ano.
 */

/** 100,00% em pontos-base. */
private const val BASE_BP = 10_000

/** 100% em partes por milhão. */
private const val BASE_PPM = 1_000_000

private const val MESES_NO_ANO = 12

/**
 * A taxa anual que o investimento realmente rende, em pontos-base, ou `null`
 * quando ela depende de um índice que o app ainda não tem. REQ-INV-002
 *
 * `PREFIXADO` devolve o que está na conta. `CDI` multiplica: 110% do CDI
 * (`11000`) sobre um CDI de 14,90% (`1490`) dá 16,39% a.a. (`1639`) — inteiro
 * exato, sem ponto flutuante, porque as duas escalas são a mesma e a divisão
 * por [BASE_BP] cancela uma delas.
 *
 * `Long` na multiplicação e não `Int`: `Int` aguentaria os valores plausíveis,
 * e estouraria em silêncio numa taxa absurda digitada por engano — 300.000% do
 * CDI cabe no campo e não cabe em `Int`.
 */
fun anualEfetivoBp(indexador: Indexador, taxaBp: Int, cdiAnualBp: Int?): Int? =
    when (indexador) {
        Indexador.PREFIXADO -> taxaBp
        Indexador.CDI -> cdiAnualBp?.let { (it.toLong() * taxaBp / BASE_BP).toInt() }
    }

/**
 * A taxa mensal equivalente a [anualBp], em partes por milhão. REQ-INV-002
 *
 * **Composta, não dividida por doze.** 14,90% a.a. dão 1,1640% a.m., não
 * 1,2417% — a diferença é meio ponto percentual ao ano, que é exatamente o
 * tamanho do erro que faria o previsto nunca bater com o extrato.
 *
 * Taxa negativa não passa: nenhum indexador do app rende menos que zero, e um
 * sinal trocado na entrada viraria rendimento negativo lançado como receita.
 */
fun mensalPpm(anualBp: Int): Int {
    require(anualBp >= 0) { "taxa anual negativa: $anualBp bp" }
    val anual = anualBp.toDouble() / BASE_BP
    val mensal = (1.0 + anual).pow(1.0 / MESES_NO_ANO) - 1.0
    return (mensal * BASE_PPM).roundToInt()
}
