package com.favicode.mymoney

/**
 * Un movimiento de dinero (ingreso o gasto).
 * Parte de Jose: Ingreso de presupuesto / gastos mensuales.
 */
data class Transaction(
    val id: Long,
    val type: Type,
    val amount: Double,
    val category: String,
    val description: String,
    val timestamp: Long
) {
    enum class Type { INCOME, EXPENSE }
}
