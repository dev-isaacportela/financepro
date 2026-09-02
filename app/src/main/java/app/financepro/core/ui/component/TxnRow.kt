package app.financepro.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.Tema
import app.financepro.domain.model.Account
import app.financepro.domain.model.Category
import app.financepro.domain.model.Txn
import app.financepro.domain.model.TxnType

/**
 * A linha de transação de [design.md](../../../../../../../../../docs/design.md) §6.3.
 *
 * Nasceu `private` em `TransactionsScreen`; subiu para cá quando o dashboard
 * (T-017) virou o segundo chamador — a mesma régua que promoveu `Chips` e
 * `Rotulo`. Duas cópias divergiriam, e a que ficaria errada é a que ninguém
 * abre.
 *
 * **Ela não traz o próprio fundo nem o recuo lateral.** Quem a coloca decide,
 * e é o que faz a mesma linha caber nos dois lugares: dentro de um bloco ela
 * herda o recuo do card e alinha com o título; na lista ela recebe os 12dp que
 * a alinham com o total do dia. Trazendo os dois de casa, o recuo dobrava dentro
 * do card e a descrição truncava.
 *
 * Quem a coloca decide também o fundo: na lista de
 * transações são linhas planas sobre o canvas, agrupadas por dia; no dashboard
 * são um bloco só dentro de um `Cartao`. Um card por linha, que era o desenho
 * anterior, transformava dez transações em dez retângulos e dava a cada uma o
 * peso de uma seção.
 *
 * Descrição e subtítulo são blocos **empilhados**, nunca lado a lado — é o erro
 * que o protótipo em HTML cometeu deixando-os como `span` inline. Sem altura
 * fixa: com fonte a 200% (REQ-A11Y-004) a linha precisa poder crescer.
 *
 * [saldoCents] só existe no extrato de uma conta: sem conta escolhida, "saldo
 * corrente" não teria de qual conta ser. O dashboard passa `null`.
 *
 * [onClick] abre a edição (T-050). Opcional porque nem todo chamador tem para
 * onde levar — e uma linha que responde ao toque sem ir a lugar nenhum é pior
 * que uma que não responde.
 */
@Composable
fun LinhaDeTransacao(
    txn: Txn,
    categoria: Category?,
    conta: Account?,
    destino: Account?,
    saldoCents: Long? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // `onClickLabel` não é enfeite: sem ele o TalkBack anuncia "toque duas vezes
    // para ativar" sem dizer para quê (REQ-A11Y-001). O alvo já passa de 48dp
    // com os 12dp de padding e duas linhas de texto (REQ-A11Y-002).
    val toque = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClickLabel = "Editar", onClick = onClick)
    }

    Box(modifier.fillMaxWidth().then(toque)) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarDeCategoria(colorArgb = categoria?.colorArgb, iconKey = categoria?.iconKey)
            Column(Modifier.weight(1f)) {
                Text(
                    text = descricaoDe(txn, categoria),
                    style = BodyStrong,
                    color = Tema.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtituloDe(txn, categoria, conta, destino),
                    style = Caption,
                    color = Tema.ink.copy(alpha = SUBTITULO_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(cents = txn.amountCents, porSinal = true)
                if (saldoCents != null) MoneyText(cents = saldoCents, style = MoneyCaption)
            }
        }
    }
}

/** Descrição vazia é comum no lançamento de 3 toques: a categoria dá o nome. */
private fun descricaoDe(txn: Txn, categoria: Category?): String =
    txn.description.ifBlank { categoria?.name ?: tipoLegivel(txn.type) }

private fun subtituloDe(txn: Txn, categoria: Category?, conta: Account?, destino: Account?): String {
    val origem = conta?.name.orEmpty()
    return if (txn.type == TxnType.TRANSFER) {
        "Transferência · " + origem + " → " + destino?.name.orEmpty()
    } else {
        listOf(categoria?.name ?: "Sem categoria", origem).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

private fun tipoLegivel(tipo: TxnType) = when (tipo) {
    TxnType.INCOME -> "Receita"
    TxnType.EXPENSE -> "Despesa"
    TxnType.TRANSFER -> "Transferência"
}

/** Os 62% de design.md §6.3 — o subtítulo recua sem sumir. */
private const val SUBTITULO_ALPHA = 0.62f
