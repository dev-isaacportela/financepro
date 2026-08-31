package com.benenutri.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.benenutri.finance.core.ui.component.MoneyText
import com.benenutri.finance.core.ui.theme.DisplaySm
import com.benenutri.finance.core.ui.theme.MoneyLg
import com.benenutri.finance.core.ui.theme.Slush
import com.benenutri.finance.core.ui.theme.SlushTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlushTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Slush.paper,
                ) { insets ->
                    Placeholder(Modifier.padding(insets))
                }
            }
        }
    }
}

/**
 * Andaime. A navegação real é a T-011; o que está aqui é o mínimo para o tema da
 * T-010 ser exercido de verdade — display type esmagado e um valor por
 * [MoneyText], que é o caminho por onde todo dinheiro do app passa.
 */
@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SLUSH", style = DisplaySm, color = Slush.ink)
        MoneyText(cents = -1_850, style = MoneyLg)
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    SlushTheme { Placeholder() }
}

@Preview(showBackground = true, backgroundColor = 0xFF111111)
@Composable
private fun PlaceholderDarkPreview() {
    SlushTheme(dark = true) { Placeholder() }
}
