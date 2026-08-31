package com.benenutri.finance

import android.app.Application

/**
 * Ponto de entrada do processo.
 *
 * Fica vazia de propósito. Hilt entra na T-009 (`@HiltAndroidApp`), o banco na
 * T-004 e a geração de recorrências na T-031 — cada um na sua task, não aqui
 * "por enquanto".
 */
class FinanceApp : Application()
