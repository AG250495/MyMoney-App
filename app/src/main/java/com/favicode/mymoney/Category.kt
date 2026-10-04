package com.favicode.mymoney

/**
 * Una categoría de ingreso o gasto.
 * iconKey: nombre del ícono (se usa en el Paso D para elegirlo).
 * colorHex: color en formato "#RRGGBB".
 */
data class Category(
    val id: Long,
    val name: String,
    val type: Transaction.Type,
    val iconKey: String,
    val colorHex: String
)