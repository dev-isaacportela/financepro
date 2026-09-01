package app.financepro.data.export

import app.financepro.data.db.AccountEntity
import app.financepro.data.db.BudgetEntity
import app.financepro.data.db.CategoryEntity
import app.financepro.data.db.RecurringRuleEntity
import app.financepro.data.db.TxnEntity
import app.financepro.domain.model.TxnType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Exportação. REQ-BAK-001
 *
 * Serializa **entidades**, não modelos de domínio: `notes`, `dedupeKey`,
 * `importBatchId`, `createdAt` e `lastGeneratedDate` não sobem para o domínio
 * (Art. 8), e um backup que os perde não é backup — a restauração da T-035
 * recriaria transações que a importação já conhecia, e o dedupe da F2 acharia
 * que são novas.
 *
 * Por isso mora em `data/` e não em `domain/`: o formato do arquivo é o formato
 * das tabelas, de propósito, e é o que torna a volta uma escrita direta em vez
 * de uma tradução que pode perder coluna.
 *
 * As duas funções são puras e recebem listas — quem lê o banco e escreve o
 * arquivo é o [ExportRepository]. É o que permite testar o CSV e o ida-e-volta
 * do JSON em JVM, sem Android e sem `ContentResolver`.
 */

/**
 * Versão do formato, não do banco.
 *
 * Existe para a restauração (T-035) poder recusar um arquivo que ela não sabe
 * ler, em vez de escrever lixo por cima do histórico. Sobe quando um campo sai
 * ou muda de significado — campo novo com valor padrão não quebra a leitura.
 */
const val SCHEMA_EXPORTACAO = 1

/**
 * A base inteira num objeto.
 *
 * ponytail: cinco tabelas, não sete. `import_batch` e `payee_rule` existem no
 * schema mas nada as escreve antes da F2, e nem DAO elas têm — exportá-las hoje
 * seria uma lista vazia com uma query inventada para produzi-la. Entram na
 * T-041, junto com o primeiro código que grava nelas, e o [SCHEMA_EXPORTACAO]
 * é o que avisa quem tentar restaurar o arquivo antigo depois disso.
 */
@Serializable
data class BaseExportada(
    val schema: Int = SCHEMA_EXPORTACAO,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val txns: List<TxnEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
)

/**
 * `prettyPrint` porque o arquivo é do usuário: um JSON de uma linha só é
 * ilegível em qualquer editor, e o custo é espaço em disco que ninguém sente.
 * `ignoreUnknownKeys` na volta para que campo novo num arquivo mais recente não
 * derrube a leitura inteira.
 */
private val JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun paraJson(base: BaseExportada): String = JSON.encodeToString(base)

fun deJson(texto: String): BaseExportada = JSON.decodeFromString(texto)

/**
 * CSV que o Excel em pt-BR abre sem tratamento. REQ-BAK-001
 *
 * Três detalhes, e cada um deles sozinho estraga o arquivo:
 *
 * - **BOM.** Sem ele o Excel lê o arquivo como ANSI e "Alimentação" vira
 *   "AlimentaÃ§Ã£o". É a razão de o arquivo começar por [BOM] e não por `Data`;
 * - **`;` como separador.** Em locale pt-BR a vírgula é o decimal, e o Excel
 *   espera ponto e vírgula. Com `,` ele joga a linha inteira numa célula só;
 * - **valor com vírgula e sem milhar.** `formatBRL` produz `−R$ 1.234,56`, com
 *   o sinal de menos tipográfico (U+2212) — bonito na tela e não numérico em
 *   planilha nenhuma. Aqui o número sai de aritmética inteira, como manda o
 *   Art. 6: nenhum `Double` entre o centavo e o arquivo.
 */
fun paraCsv(
    txns: List<TxnEntity>,
    contas: List<AccountEntity>,
    categorias: List<CategoryEntity>,
): String {
    val nomeDaConta = contas.associate { it.id to it.name }
    val nomeDaCategoria = categorias.associate { it.id to it.name }

    val linhas = txns.sortedWith(compareBy({ it.date }, { it.id })).map { txn ->
        listOf(
            DIA.format(LocalDate.ofEpochDay(txn.date)),
            txn.description,
            txn.categoryId?.let { nomeDaCategoria[it] }.orEmpty(),
            nomeDaConta[txn.accountId].orEmpty(),
            txn.counterAccountId?.let { nomeDaConta[it] }.orEmpty(),
            rotuloDoTipo(txn.type),
            valorCsv(txn.amountCents),
            if (txn.cleared) "Sim" else "Não",
        ).joinToString(SEPARADOR) { escapar(it) }
    }

    return BOM + (listOf(CABECALHO.joinToString(SEPARADOR)) + linhas).joinToString(FIM_DE_LINHA) +
        FIM_DE_LINHA
}

/** Centavos para `-1234,56`. Inteiro do começo ao fim (Art. 6). */
private fun valorCsv(cents: Long): String {
    val sinal = if (cents < 0) "-" else ""
    val absoluto = abs(cents)
    return sinal + (absoluto / CENTAVOS) + "," + (absoluto % CENTAVOS).toString().padStart(2, '0')
}

/**
 * Aspas só quando precisa, e aspa interna dobrada — é o que o RFC 4180 manda e
 * o que impede uma descrição com ponto e vírgula de virar duas colunas.
 */
private fun escapar(campo: String): String =
    if (campo.any { it in PRECISA_DE_ASPAS }) {
        "\"" + campo.replace("\"", "\"\"") + "\""
    } else {
        campo
    }

private fun rotuloDoTipo(tipo: TxnType) = when (tipo) {
    TxnType.INCOME -> "Receita"
    TxnType.EXPENSE -> "Despesa"
    TxnType.TRANSFER -> "Transferência"
}

private val CABECALHO = listOf(
    "Data",
    "Descrição",
    "Categoria",
    "Conta",
    "Para",
    "Tipo",
    "Valor",
    "Efetivada",
)

/** `U+FEFF`. Ver o KDoc de [paraCsv]. */
private const val BOM = "﻿"
private const val SEPARADOR = ";"

/** O separador, a própria aspa, e as duas metades do fim de linha. */
private const val PRECISA_DE_ASPAS = ";\"\n\r"

/** CRLF, como o RFC 4180 pede — e é o que o Excel do Windows espera. */
private const val FIM_DE_LINHA = "\r\n"
private const val CENTAVOS = 100L

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
