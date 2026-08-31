package app.financepro.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financepro.core.ui.theme.BodyStrong
import app.financepro.core.ui.theme.Caption
import app.financepro.core.ui.theme.MoneyCaption
import app.financepro.core.ui.theme.OutlineWidth
import app.financepro.core.ui.theme.Pill
import app.financepro.core.ui.theme.Slush
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
 * Descrição e subtítulo são blocos **empilhados**, nunca lado a lado — é o erro
 * que o protótipo em HTML cometeu deixando-os como `span` inline. Sem altura
 * fixa: com fonte a 200% (REQ-A11Y-004) a linha precisa poder crescer.
 *
 * [saldoCents] só existe no extrato de uma conta: sem conta escolhida, "saldo
 * corrente" não teria de qual conta ser. O dashboard passa `null`.
 */
@Composable
fun LinhaDeTransacao(
    txn: Txn,
    categoria: Category?,
    conta: Account?,
    destino: Account?,
    saldoCents: Long? = null,
    modifier: Modifier = Modifier,
) {
    SlushCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PontoDeCategoria(categoria?.colorArgb)
            Column(Modifier.weight(1f)) {
                Text(
                    text = descricaoDe(txn, categoria),
                    style = BodyStrong,
                    color = Slush.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtituloDe(txn, categoria, conta, destino),
                    style = Caption,
                    color = Slush.ink.copy(alpha = SUBTITULO_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(cents = txn.amountCents)
                if (saldoCents != null) MoneyText(cents = saldoCents, style = MoneyCaption)
            }
        }
    }
}

/**
 * Ponto de 10dp, não sticker. O contorno não é decoração: sem ele um ponto
 * Sunburst sobre papel branco dá 1.40:1 e some — leria como falha de
 * renderização, não como escolha (design.md §6.3).
 */
@Composable
private fun PontoDeCategoria(colorArgb: Int?) = Box(
    Modifier
        .padding(top = 5.dp)
        .size(10.dp)
        .clip(Pill)
        .background(colorArgb?.let { Color(it) } ?: Slush.paper)
        .border(OutlineWidth, Slush.ink, Pill),
)

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
