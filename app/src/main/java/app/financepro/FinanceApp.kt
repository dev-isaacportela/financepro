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
 * A geração de recorrências (T-031) roda na `MainActivity`, e não aqui: ela
 * precisa do banco, e abri-lo no `onCreate` do processo atrasaria todo
 * lançamento do app pelo mesmo motivo acima.
 */
@HiltAndroidApp
class FinanceApp : Application()
