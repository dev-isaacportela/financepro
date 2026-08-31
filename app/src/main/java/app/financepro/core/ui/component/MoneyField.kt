package app.financepro.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import app.financepro.core.money.formatBRL
import app.financepro.core.ui.theme.MoneyLg
import app.financepro.core.ui.theme.Slush

/**
 * Campo de valor do lançamento rápido. REQ-UI-002 · REQ-CORE-001 · REQ-CORE-005
 *
 * Digita-se **centavos**, da direita para a esquerda, como numa maquininha: `1`,
 * `8`, `5`, `0` vira `R$ 18,50`. Sem vírgula para posicionar, sem estado
 * intermediário inválido, e sem o erro clássico de digitar "18" esperando
 * dezoito reais e gravar dezoito centavos. O teclado é numérico puro — ponto e
 * sinal nem aparecem, porque não há o que fazer com eles aqui.
 *
 * O que a tela mostra é [formatBRL] do acumulado, via `visualTransformation`: a
 * formatação pt-BR continua com uma fonte só, a mesma de [MoneyText].
 *
 * [autoFocus] existe porque o foco automático é certo num lugar só: a folha de
 * lançamento, onde REQ-UI-002 exige o teclado já aberto e um toque a mais seria
 * o quarto num fluxo de três. Num formulário com dois campos de dinheiro —
 * saldo de abertura e limite do cartão — todos pedindo foco, o último composto
 * rouba o cursor e o usuário digita no campo errado. Padrão desligado.
 */
@Composable
fun MoneyField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

    // Zero vira campo vazio, e o cursor fica sempre no fim: com dígitos entrando
    // pela direita, um cursor no meio faria a próxima tecla cair no lugar errado.
    val digitos = if (cents == 0L) "" else cents.toString()
    val valor = TextFieldValue(digitos, TextRange(digitos.length))

    BasicTextField(
        value = valor,
        onValueChange = { novo ->
            val apenasDigitos = novo.text.filter { it.isDigit() }.take(MAX_DIGITOS)
            onCentsChange(apenasDigitos.toLongOrNull() ?: 0L)
        },
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        textStyle = MoneyLg.copy(color = Slush.ink, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        cursorBrush = SolidColor(Slush.ink),
        visualTransformation = MoedaBrl,
    )
}

/**
 * Dígitos crus → `R$ 1.234,56`.
 *
 * O mapeamento de deslocamento é constante — sempre o fim — e isso é correto
 * aqui **porque** o campo já força o cursor para o fim a cada tecla. Um
 * mapeamento posição a posição teria de acompanhar ponto de milhar e vírgula, e
 * erraria na primeira mudança de casa.
 */
private val MoedaBrl = VisualTransformation { texto ->
    val formatado = AnnotatedString(formatBRL(texto.text.toLongOrNull() ?: 0L))
    TransformedText(
        formatado,
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = formatado.length
            override fun transformedToOriginal(offset: Int) = texto.length
        },
    )
}

/** `R$ 99.999.999,99` já é mais do que qualquer lançamento manual honesto. */
private const val MAX_DIGITOS = 10
