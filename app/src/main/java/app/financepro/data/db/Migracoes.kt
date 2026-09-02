package app.financepro.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * As migrações do banco. Art. 12 · REQ-DATA-001
 *
 * `fallbackToDestructiveMigration` não existe neste projeto e não vai existir:
 * apagar o histórico financeiro de alguém para vencer um erro de migração não é
 * uma opção, e `tools/trace.py` reprova o build se aparecer.
 */

/**
 * v1 → v2 — a paleta do sistema visual anterior sai do banco.
 *
 * **Só dados, nenhum esquema.** A troca de sistema visual
 * ([ADR-011](../../../../../../../../docs/decisoes.md)) reescreveu `Acentos` e o
 * seed, mas o seed roda **uma vez**, em banco vazio: quem já tinha o app
 * instalado ficou com seis cores que não existem mais na paleta — Electric Blue,
 * Mint Pop, Lavender, Ember, Sunburst e Voltage Violet — e o seletor não as
 * oferece mais, então nem editando dava para chegar de volta nelas.
 *
 * `TokenLintTest` não pegava isso: ele compara `CATEGORIAS_PADRAO` com `Acentos`,
 * que são duas constantes. Linha gravada no banco nenhum teste de fonte alcança.
 *
 * **O que a migração não faz:** inventar cor para quem escolheu uma. O `WHERE`
 * casa exatamente os seis hexadecimais antigos; qualquer outro valor fica como
 * está. Quem tiver escolhido Lavender de propósito recebe Rosa no lugar — é a
 * única perda, e a alternativa era deixar a categoria numa cor que o app não
 * sabe mais desenhar.
 *
 * Os valores são `Int` com sinal porque é assim que o Room grava ARGB, e um
 * literal sem sinal não casaria com nenhuma linha — o erro silencioso clássico
 * desta migração.
 */
val DE_1_PARA_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val de = longArrayOf(
            -11689217, // Electric Blue
            -11150436, // Mint Pop
            -1454849, // Lavender
            -308989, // Ember
            -10447, // Sunburst
            -10728738, // Voltage Violet
            -3355444, // Concrete Gray — o padrão de conta nova até a T-053
        )
        val para = longArrayOf(
            -16745534, // Azul
            -16734082, // Verde-azulado
            -1696183, // Rosa
            -1278464, // Laranja
            -5206016, // Amarelo
            -11972641, // Violeta
            -16745534, // Azul
        )

        // Duas tabelas guardam cor: categoria e conta. A regra é a mesma nas
        // duas, e escrever o laço aqui é menos que duplicar sete `UPDATE`.
        listOf("category", "account").forEach { tabela ->
            de.indices.forEach { i ->
                db.execSQL(
                    "UPDATE $tabela SET colorArgb = ? WHERE colorArgb = ?",
                    arrayOf<Any>(para[i], de[i]),
                )
            }
        }
    }
}

/** Na ordem, para o builder não depender de alguém lembrar de listá-las. */
val MIGRACOES = arrayOf(DE_1_PARA_2)
