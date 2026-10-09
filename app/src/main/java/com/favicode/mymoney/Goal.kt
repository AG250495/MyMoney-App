package com.favicode.mymoney

data class Goal(
    val id: Long = 0,
    val amount: Double,
    val monthName: String, // Ejemplo: "Octubre"
    val year: Int,
    val createdAt: String
)