package app.financepro

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Ponto de entrada do processo.
 *
 * `@HiltAndroidApp` é a única coisa aqui, e é o suficiente: o banco vem do
 * `DatabaseModule`, construído sob demanda na primeira injeção. Abrir o banco no
 * `onCreate` atrasaria o start de todo lançamento do app para um trabalho que a
 * primeira tela já dispara.
 *
 * A geração de recorrências entra na T-031, como Worker — não aqui.
 */
@HiltAndroidApp
class FinanceApp : Application()
