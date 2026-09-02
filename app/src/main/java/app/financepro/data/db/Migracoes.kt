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

/**
 * v2 → v3 — cada despesa padrão ganha a própria cor.
 *
 * A distribuição anterior usava seis acentos para nove despesas e repetia três.
 * Numa lista dá para conviver, porque o nome vem ao lado; no gráfico de pizza do
 * relatório, duas fatias da mesma cor viram uma mancha só e a legenda deixa de
 * explicar qual é qual — foi ali que o defeito apareceu.
 *
 * **Cada id tem duas cores "de antes", e não uma.** É a armadilha desta
 * migração, e a primeira versão caiu nela: quem instalou depois da troca de
 * paleta foi semeado com a distribuição intermediária, enquanto quem já tinha o
 * app recebeu o resultado de [DE_1_PARA_2] — que partiu das cores do sistema
 * visual antigo e chegou em outros valores. Casar só com a primeira fazia a
 * migração não encostar em nenhuma linha do segundo grupo, em silêncio, e a
 * repetição continuava exatamente onde incomodava.
 *
 * **Só mexe em quem o app atribuiu.** O `WHERE` casa `id` **e** uma das duas
 * cores que o app poderia ter posto ali. Se o usuário escolheu outra, a linha
 * não casa e fica como está — é a diferença entre corrigir o que o app fez e
 * sobrescrever o que a pessoa fez.
 *
 * Categoria criada pelo usuário nunca entra: os ids 1 a 10 são os do seed, e
 * `Seed.kt` os declara explicitamente por essa razão.
 */
val DE_2_PARA_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val teal = -16734082L
        val azul = -16745534L
        val verde = -12417511L
        val amarelo = -5206016L
        val laranja = -1278464L
        val rosa = -1696183L
        val vermelho = -1950902L
        val marrom = -7115422L
        val violeta = -11972641L

        // id, as duas cores que o app pode ter posto, e a de agora.
        val troca = listOf(
            Cor(1, listOf(teal, azul), teal), // Alimentação
            Cor(2, listOf(verde, rosa), violeta), // Transporte
            Cor(3, listOf(marrom, violeta), rosa), // Moradia
            Cor(4, listOf(azul, teal), marrom), // Saúde
            Cor(5, listOf(rosa, amarelo), laranja), // Lazer
            Cor(6, listOf(amarelo, laranja), amarelo), // Educação
            Cor(7, listOf(verde, rosa), verde), // Compras
            Cor(8, listOf(azul, teal), azul), // Assinaturas
            Cor(9, listOf(amarelo), teal), // Salário
            Cor(10, listOf(teal, azul), vermelho), // Outros
        )

        troca.forEach { (id, antes, agora) ->
            val marcadores = antes.joinToString(",") { "?" }
            db.execSQL(
                "UPDATE category SET colorArgb = ? WHERE id = ? AND colorArgb IN ($marcadores)",
                (listOf<Any>(agora, id) + antes).toTypedArray(),
            )
        }
    }
}

/** Uma troca de cor: o id, o que o app pode ter posto ali, e o que fica. */
private data class Cor(val id: Long, val antes: List<Long>, val agora: Long)

/**
 * v3 → v4 — conta de investimento ganha indexador e taxa. REQ-INV-001
 *
 * Duas colunas nuláveis em `account`, no mesmo molde das quatro que só valem
 * para cartão. `ALTER TABLE ADD COLUMN` é a única forma de DDL que o SQLite faz
 * sem recriar a tabela, e nuláveis não precisam de `DEFAULT` — que é justamente
 * por que o modelo as declara assim.
 *
 * **A categoria "Rendimentos" não entra aqui.** Seria a linha óbvia a
 * acrescentar, e é a errada: os ids 1 a 10 são do seed, então o 11 é o da
 * primeira categoria que o usuário criou. Um `INSERT OR IGNORE` naquele id
 * seria ignorado em silêncio em toda instalação antiga, e o rendimento cairia
 * na categoria que a pessoa chamou de "Pets". Quem cria a categoria é
 * `CategoryRepository.idDeRendimentos()`, com id vindo do AUTOINCREMENT, na
 * primeira vez que um rendimento é lançado.
 */
val DE_3_PARA_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN indexador TEXT")
        db.execSQL("ALTER TABLE account ADD COLUMN taxaBp INTEGER")
    }
}

/** Na ordem, para o builder não depender de alguém lembrar de listá-las. */
val MIGRACOES = arrayOf(DE_1_PARA_2, DE_2_PARA_3, DE_3_PARA_4)
