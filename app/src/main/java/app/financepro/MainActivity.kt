package app.financepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.financepro.core.ui.theme.SlushTheme
import app.financepro.feature.FinanceNav
import dagger.hilt.android.AndroidEntryPoint

/**
 * Só o tema e o grafo de navegação. Nenhuma tela mora aqui: a `Activity` é a
 * casca do processo, e conteúdo dentro dela é conteúdo que nenhuma rota alcança.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlushTheme {
                FinanceNav()
            }
        }
    }
}
