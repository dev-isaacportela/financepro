package app.financepro.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.financepro.domain.model.CategoryKind

/**
 * Semente do banco. REQ-CAT-004 · REQ-ACT-003
 *
 * Roda em `onCreate`, que o Room chama **uma vez**, na criação do arquivo. Não
 * há flag de "já semeei" em DataStore para desincronizar, nem `INSERT OR
 * IGNORE` a cada abertura: o próprio ciclo de vida do banco é a garantia.
 *
 * SQL cru porque em `onCreate` os DAOs ainda não existem — a instância do Room
 * está sendo construída. Os valores vão como parâmetros ligados, nunca
 * concatenados: nome de categoria com apóstrofo quebraria a query montada à mão.
 */
val SeedCallback = object : RoomDatabase.Callback() {
    // `arrayOf<Any>` explícito: sem ele o Kotlin infere a interseção de
    // Comparable e Serializable, avisa que vai virar erro, e o `execSQL` só quer
    // Any mesmo.
    override fun onCreate(db: SupportSQLiteDatabase) {
        CATEGORIAS_PADRAO.forEach { c ->
            db.execSQL(
                "INSERT INTO category (id, name, kind, parentId, iconKey, colorArgb, archived, useCount) " +
                    "VALUES (?, ?, ?, NULL, ?, ?, 0, 0)",
                arrayOf<Any>(c.id, c.nome, c.kind.name, c.iconKey, c.corArgb),
            )
        }
        REGRAS_PADRAO.forEach { (chave, categoriaId) ->
            db.execSQL(
                "INSERT INTO payee_rule (normalizedKey, categoryId, hitCount) VALUES (?, ?, 1)",
                arrayOf<Any>(chave, categoriaId),
            )
        }
    }
}

/**
 * As cores são os stickers de [design.md](../../../../../../../../docs/design.md)
 * §1, como Int ARGB com sinal. Ficam aqui, e não em `core/ui/theme/`, porque
 * `data/` não importa nada de Compose — o tema declara os mesmos hex do lado
 * dele (T-010). São seis stickers para dez categorias; a repetição é do design.
 */
internal const val ELECTRIC_BLUE = 0xFF4DA2FF.toInt()
internal const val MINT_POP = 0xFF55DB9C.toInt()
internal const val LAVENDER = 0xFFE9CCFF.toInt()
internal const val EMBER = 0xFFFB4903.toInt()
internal const val SUNBURST = 0xFFFFD731.toInt()
internal const val VOLTAGE_VIOLET = 0xFF5C4ADE.toInt()

/** As contas do onboarding (REQ-UI-005) saem da mesma paleta, não de hex soltos. */
internal const val ACCOUNT_CASH_COLOR = MINT_POP
internal const val ACCOUNT_CHECKING_COLOR = ELECTRIC_BLUE

internal data class CategoriaPadrao(
    val id: Long,
    val nome: String,
    val kind: CategoryKind,
    val iconKey: String,
    val corArgb: Int,
)

/**
 * REQ-CAT-004 — as dez da spec, nesta ordem.
 *
 * O `id` é explícito porque [REGRAS_PADRAO] aponta para ele. Deixar o
 * AUTOINCREMENT decidir daria os mesmos números hoje e um bug silencioso no dia
 * em que alguém inserisse uma categoria antes destas.
 *
 * **As cores são distribuídas pela ordem alfabética, não pelo id.** São seis
 * stickers para dez categorias, então repetir é inevitável — o que dá para
 * escolher é *onde* repete. Enquanto todo `useCount` é zero, o grid sai em
 * ordem alfabética, que é exatamente o que o usuário novo vê; percorrendo essa
 * ordem em ciclo pela paleta, duas categorias da mesma cor ficam sempre seis
 * posições distantes e nunca vizinhas.
 *
 * A versão anterior atribuía por id e punha Salário e Saúde, lado a lado no
 * grid, com o mesmo Mint Pop. `SeedTest` agora falha se isso voltar.
 */
internal val CATEGORIAS_PADRAO = listOf(
    CategoriaPadrao(1, "Alimentação", CategoryKind.EXPENSE, "utensils", ELECTRIC_BLUE),
    CategoriaPadrao(2, "Transporte", CategoryKind.EXPENSE, "car", LAVENDER),
    CategoriaPadrao(3, "Moradia", CategoryKind.EXPENSE, "home", VOLTAGE_VIOLET),
    CategoriaPadrao(4, "Saúde", CategoryKind.EXPENSE, "cross", MINT_POP),
    CategoriaPadrao(5, "Lazer", CategoryKind.EXPENSE, "confetti", SUNBURST),
    CategoriaPadrao(6, "Educação", CategoryKind.EXPENSE, "book", EMBER),
    CategoriaPadrao(7, "Compras", CategoryKind.EXPENSE, "bag", LAVENDER),
    CategoriaPadrao(8, "Assinaturas", CategoryKind.EXPENSE, "repeat", MINT_POP),
    // Sozinha no grid de receita: vizinhança não é problema dela.
    CategoriaPadrao(9, "Salário", CategoryKind.INCOME, "cash", SUNBURST),
    CategoriaPadrao(10, "Outros", CategoryKind.EXPENSE, "dots", ELECTRIC_BLUE),
)

/**
 * REQ-ACT-003 — para a primeira importação não chegar vazia.
 *
 * As chaves já estão na forma que `normalize` (T-036) produz: maiúsculas, sem
 * acento, sem sequência de 4+ dígitos, espaço simples. Uma chave com acento
 * nunca casaria com nada, e o erro só apareceria na F2.
 *
 * `Salário` e `Outros` não aparecem: receita não vem de estabelecimento, e
 * "Outros" é o destino de quem não casou com regra nenhuma.
 *
 * Estas chaves são **palavra-chave**, não descrição inteira: um extrato traz
 * `UBER *TRIP HELP.UBER.COM`, nunca `UBER`. Quem casa é a T-040, e ela precisa
 * ancorar em limite de palavra — `TIM` dentro de `OTIMO` é o caso que prova.
 */
internal val REGRAS_PADRAO: List<Pair<String, Long>> = listOf(
    // Alimentação
    "IFOOD" to 1L,
    "RAPPI" to 1L,
    "ZE DELIVERY" to 1L,
    "MCDONALDS" to 1L,
    "BURGER KING" to 1L,
    "SUBWAY" to 1L,
    "STARBUCKS" to 1L,
    "PADARIA" to 1L,
    "SUPERMERCADO" to 1L,
    "CARREFOUR" to 1L,
    "PAO DE ACUCAR" to 1L,
    "ASSAI" to 1L,
    "ATACADAO" to 1L,
    // Transporte
    "UBER" to 2L,
    "99APP" to 2L,
    "CABIFY" to 2L,
    "IPIRANGA" to 2L,
    "SHELL" to 2L,
    "PETROBRAS" to 2L,
    "ESTACIONAMENTO" to 2L,
    "SEM PARAR" to 2L,
    "CONECTCAR" to 2L,
    // Moradia
    "ENEL" to 3L,
    "LIGHT SERVICOS" to 3L,
    "CEMIG" to 3L,
    "SABESP" to 3L,
    "COMGAS" to 3L,
    "VIVO" to 3L,
    "CLARO" to 3L,
    "TIM" to 3L,
    // Saúde
    "DROGASIL" to 4L,
    "DROGARIA" to 4L,
    "RAIA" to 4L,
    "PACHECO" to 4L,
    "UNIMED" to 4L,
    "HAPVIDA" to 4L,
    // Lazer
    "CINEMARK" to 5L,
    "INGRESSO COM" to 5L,
    "STEAMGAMES" to 5L,
    // Educação
    "UDEMY" to 6L,
    "ALURA" to 6L,
    "COURSERA" to 6L,
    // Compras
    "AMAZON" to 7L,
    "MERCADOLIVRE" to 7L,
    "MAGAZINE LUIZA" to 7L,
    "AMERICANAS" to 7L,
    "SHOPEE" to 7L,
    "ALIEXPRESS" to 7L,
    // Assinaturas
    "NETFLIX" to 8L,
    "SPOTIFY" to 8L,
    "DISNEY PLUS" to 8L,
    "HBO MAX" to 8L,
    "PRIME VIDEO" to 8L,
    "YOUTUBEPREMIUM" to 8L,
    "APPLE COM" to 8L,
    "GOOGLE" to 8L,
)
