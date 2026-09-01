package app.financepro.data.export

import app.financepro.core.testing.Req
import app.financepro.data.db.CATEGORIA
import app.financepro.data.db.CONTA
import app.financepro.data.db.LANCAMENTO
import app.financepro.data.db.dia
import app.financepro.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-BAK-001 — o arquivo que sai do app.
 *
 * O que se testa aqui é o que **não** dá para ver olhando a tela: o BOM que o
 * Excel exige, o separador que o pt-BR espera, a vírgula decimal, e o ida-e-volta
 * do JSON sem perder as colunas que o domínio não carrega. Um export quebrado só
 * aparece no dia em que alguém precisa dele, que é o pior dia possível.
 */
@Req("REQ-BAK-001")
class ExportTest {

    private val corrente = CONTA.copy(id = 1, name = "Conta corrente")
    private val poupanca = CONTA.copy(id = 2, name = "Poupança")
    private val alimentacao = CATEGORIA.copy(id = 10, name = "Alimentação")

    private val despesa = LANCAMENTO.copy(
        id = 100,
        accountId = 1,
        categoryId = 10,
        amountCents = -1_234_56,
        date = dia(2026, 3, 7),
        description = "Padaria",
    )

    private fun csv(vararg txns: app.financepro.data.db.TxnEntity) =
        paraCsv(txns.toList(), listOf(corrente, poupanca), listOf(alimentacao))

    private fun linhas(texto: String) = texto.removePrefix(BOM_ESPERADO).trim().split("\r\n")

    @Test
    fun `o arquivo comeca com BOM, senao o Excel come os acentos`() {
        val texto = csv(despesa)

        assertTrue("sem BOM", texto.startsWith(BOM_ESPERADO))
        assertTrue("acento perdido", texto.contains("Alimentação"))
    }

    @Test
    fun `separador e ponto e virgula, e o decimal e virgula`() {
        val corpo = linhas(csv(despesa))[1]

        // Ponto de milhar quebraria a célula em pt-BR tanto quanto a vírgula
        // separando colunas: R$ 1.234,56 vira "1" e "234,56" em duas células.
        assertTrue(corpo, corpo.contains(";-1234,56;"))
        assertFalse("milhar no número", corpo.contains("1.234"))
    }

    @Test
    fun `centavos abaixo de dez ganham o zero da frente`() {
        val corpo = linhas(csv(despesa.copy(amountCents = -1_05)))[1]

        // Sem o padStart isto sairia "-1,5", que é um décimo do valor.
        assertTrue(corpo, corpo.contains(";-1,05;"))
    }

    @Test
    fun `cabecalho, uma linha por transacao, e fim de linha CRLF`() {
        val texto = csv(despesa, despesa.copy(id = 101))

        assertTrue("sem CRLF", texto.contains("\r\n"))
        assertEquals(3, linhas(texto).size)
        assertTrue(linhas(texto).first().startsWith("Data;Descrição;"))
    }

    @Test
    fun `descricao com ponto e virgula nao vira duas colunas`() {
        val corpo = linhas(csv(despesa.copy(description = "Pão; leite")))[1]

        assertTrue(corpo, corpo.contains("\"Pão; leite\""))
        // Oito colunas, e não nove: as aspas seguram o separador de dentro.
        assertEquals(8, colunas(corpo).size)
    }

    @Test
    fun `aspas na descricao sao dobradas`() {
        val corpo = linhas(csv(despesa.copy(description = "Mercado \"do Zé\"")))[1]

        assertTrue(corpo, corpo.contains("\"Mercado \"\"do Zé\"\"\""))
    }

    @Test
    fun `transferencia leva a conta de destino, e a data sai em dd-mm-aaaa`() {
        val corpo = linhas(
            csv(
                despesa.copy(
                    type = TxnType.TRANSFER,
                    counterAccountId = 2,
                    categoryId = null,
                ),
            ),
        )[1]
        val campos = colunas(corpo)

        assertEquals("07/03/2026", campos[0])
        assertEquals("Poupança", campos[4])
        assertEquals("Transferência", campos[5])
    }

    @Test
    fun `a lista sai em ordem de data`() {
        val texto = csv(
            despesa.copy(id = 1, date = dia(2026, 3, 20), description = "Depois"),
            despesa.copy(id = 2, date = dia(2026, 3, 2), description = "Antes"),
        )

        assertEquals(listOf("Antes", "Depois"), linhas(texto).drop(1).map { colunas(it)[1] })
    }

    // ---------- JSON ----------

    @Test
    fun `o JSON volta igual ao que foi, com as colunas que o dominio nao carrega`() {
        val base = BaseExportada(
            accounts = listOf(corrente, poupanca),
            categories = listOf(alimentacao),
            txns = listOf(
                despesa.copy(
                    notes = "com observação",
                    dedupeKey = "abc123",
                    createdAt = 1_700_000_000_000,
                    updatedAt = 1_700_000_000_001,
                ),
            ),
        )

        val voltou = deJson(paraJson(base))

        // Igualdade de `data class`: campo a campo, inclusive os quatro que o
        // modelo de domínio não tem e que uma exportação de `Txn` perderia.
        assertEquals(base, voltou)
        assertEquals("abc123", voltou.txns.single().dedupeKey)
    }

    @Test
    fun `o arquivo declara a versao do formato`() {
        // É o que permite a restauração (T-035) recusar um arquivo que ela não
        // sabe ler, em vez de escrever lixo por cima do histórico.
        assertTrue(paraJson(BaseExportada()).contains("\"schema\": $SCHEMA_EXPORTACAO"))
    }

    @Test
    fun `campo desconhecido no arquivo nao derruba a leitura`() {
        val comExtra = paraJson(BaseExportada()).replaceFirst("{", "{\n  \"futuro\": 1,")

        assertEquals(SCHEMA_EXPORTACAO, deJson(comExtra).schema)
    }

    /** Divide respeitando as aspas, que é o que o `escapar` do CSV produz. */
    private fun colunas(linha: String): List<String> {
        val campos = mutableListOf<String>()
        val atual = StringBuilder()
        var dentro = false
        linha.forEach { c ->
            when {
                c == '"' -> dentro = !dentro
                c == ';' && !dentro -> {
                    campos += atual.toString()
                    atual.clear()
                }
                else -> atual.append(c)
            }
        }
        campos += atual.toString()
        return campos
    }

    private companion object {
        const val BOM_ESPERADO = "﻿"
    }
}
