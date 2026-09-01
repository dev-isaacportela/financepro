package app.financepro.data.ingest

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Motor de deduplicação. REQ-IMP-007 · REQ-IMP-008 · REQ-IMP-009 ·
 * [ingestao.md](../../../../../../../../docs/ingestao.md) §3
 *
 * Três níveis, do mais confiável ao mais frouxo, e **só os dois primeiros
 * decidem sozinhos**:
 *
 * 1. **Chave exata.** `FITID` do banco, ou o hash de conta+dia+valor+descrição
 *    normalizada. Igual é a mesma transação: descarta;
 * 2. **A mesma chave duas vezes no próprio arquivo.** Acontece — a T-037 tem
 *    fixture com `FITID` repetido —, e sem esta linha a segunda cópia entraria
 *    como novidade e depois esbarraria no índice único do banco, já na gravação;
 * 3. **Janela de ±3 dias com valor idêntico.** Aqui o app **não decide**
 *    (REQ-IMP-009): marca, mostra as duas lado a lado, e quem escolhe é o dono
 *    do dinheiro. Duas compras de R$ 20 na mesma padaria no mesmo dia são
 *    normais, e descartar por heurística perde transação legítima.
 *
 * Puro, e recebe listas: quem lê o banco e quem grava são a T-041. É o que
 * permite provar as três regras em JVM, com datas escolhidas, sem emulador.
 *
 * **Uma conta por vez.** Todas as comparações pressupõem que [jaGravadas] já é o
 * histórico da conta de destino — valor igual no mesmo dia em contas diferentes
 * são duas transações, não uma.
 */

/** Dias para cada lado na janela do nível 3. REQ-IMP-009 */
const val JANELA_DE_DUPLICATA = 3L

/** O que a importação propõe gravar. */
data class Candidata(
    val date: LocalDate,
    val amountCents: Long,
    val description: String,
    /** [chaveOfx] quando o arquivo traz `FITID`, [dedupeKey] quando não. */
    val dedupeKey: String,
)

/** Uma linha que já está no banco, reduzida ao que o dedupe compara. */
data class JaGravada(
    val id: Long,
    val date: LocalDate,
    val amountCents: Long,
    val description: String,
    val dedupeKey: String? = null,
)

enum class Veredito {
    NOVA,

    /** Descartada sem perguntar: chave exata igual (REQ-IMP-007, REQ-IMP-008). */
    DUPLICATA,

    /** Marcada, exibida lado a lado, decidida pelo usuário (REQ-IMP-009). */
    POSSIVEL_DUPLICATA,
}

/**
 * A candidata e o que se descobriu sobre ela.
 *
 * [parecida] é o que a tela de revisão exibe ao lado quando o veredito é
 * [Veredito.POSSIVEL_DUPLICATA]: sem a outra linha à vista, "possível duplicata"
 * é um aviso que ninguém consegue julgar.
 */
data class Avaliada(
    val candidata: Candidata,
    val veredito: Veredito,
    val parecida: JaGravada? = null,
)

/**
 * Classifica cada candidata contra o histórico da conta. REQ-IMP-007 a REQ-IMP-009
 *
 * A ordem das candidatas é preservada: a tela de revisão mostra o arquivo na
 * ordem em que ele veio, e reordenar aqui faria o usuário procurar a linha que
 * ele acabou de ver no extrato do banco.
 */
fun avaliar(
    candidatas: List<Candidata>,
    jaGravadas: List<JaGravada>,
    janelaDias: Long = JANELA_DE_DUPLICATA,
): List<Avaliada> {
    val porChave = jaGravadas.filter { it.dedupeKey != null }.associateBy { it.dedupeKey }
    val vistas = mutableSetOf<String>()

    return candidatas.map { candidata ->
        val exata = porChave[candidata.dedupeKey]
        when {
            exata != null -> Avaliada(candidata, Veredito.DUPLICATA, exata)
            !vistas.add(candidata.dedupeKey) -> Avaliada(candidata, Veredito.DUPLICATA)
            else -> comJanela(candidata, jaGravadas, janelaDias)
        }
    }
}

/**
 * O nível 3, e a razão de ele não filtrar por "descrição diferente" como a spec
 * escreve: descrição **igual** com data deslocada em um dia é a duplicata mais
 * óbvia que existe, e ela não bate no nível 1 porque a data entra no hash.
 * Filtrar por descrição diferente deixaria justamente esse caso passar como
 * novidade. A janela é um superconjunto do que REQ-IMP-009 pede, e erra para o
 * lado de perguntar — que é o lado que o requisito escolhe.
 *
 * Entre várias candidatas na janela, a **mais próxima em data**: é a que o
 * usuário reconhece como sendo a mesma compra.
 */
private fun comJanela(candidata: Candidata, jaGravadas: List<JaGravada>, janelaDias: Long): Avaliada {
    val parecida = jaGravadas
        .filter { it.amountCents == candidata.amountCents && distancia(it, candidata) <= janelaDias }
        .minByOrNull { distancia(it, candidata) }

    return if (parecida == null) {
        Avaliada(candidata, Veredito.NOVA)
    } else {
        Avaliada(candidata, Veredito.POSSIVEL_DUPLICATA, parecida)
    }
}

private fun distancia(gravada: JaGravada, candidata: Candidata): Long =
    abs(ChronoUnit.DAYS.between(gravada.date, candidata.date))
