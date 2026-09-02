package app.financepro.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.financepro.data.repo.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Onboarding. REQ-UI-005 · REQ-DS-009
 *
 * **Uma tela, uma pergunta.** Quem instala um app de finanças quer registrar um
 * gasto, não preencher cadastro — o app precisa ser utilizável em menos de 30
 * segundos, e cada campo a mais aqui é uma chance a mais de desistir.
 *
 * As duas contas saem prontas: `CASH` zerada e `CHECKING` com o valor informado.
 * O resto do cadastro é da T-015, quando o usuário quiser.
 *
 * É o único **pôster completo** do app (REQ-DS-009): `DisplayXl` a 64sp com
 * entrelinha travada em 1.0, que em 360dp ocupa quase a largura toda. O canvas
 * preto sangra até a borda — é a única tela do app em que ele não é fundo de
 * card, e sim o próprio pôster.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val contas: AccountRepository,
) : ViewModel() {
    fun concluir(saldoCents: Long) = viewModelScope.launch { contas.criarIniciais(saldoCents) }
}
