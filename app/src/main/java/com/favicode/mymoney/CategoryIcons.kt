package com.favicode.mymoney

/**
 * Relaciona la clave de ícono que se guarda en la categoría ("food")
 * con el dibujo real (R.drawable.ic_cat_food).
 */
object CategoryIcons {

    val palette: List<String> = listOf(
        "#00E676", "#00D0B3", "#29B6F6", "#5C6BC0", "#AB47BC", "#EC407A",
        "#EF5350", "#FF7043", "#FFB300", "#8D6E63", "#78909C", "#C0CA33"
    )

    val icons: Map<String, Int> = linkedMapOf(
        "food" to R.drawable.ic_cat_food,
        "services" to R.drawable.ic_cat_services,
        "entertainment" to R.drawable.ic_cat_entertainment,
        "transport" to R.drawable.ic_cat_transport,
        "health" to R.drawable.ic_cat_health,
        "education" to R.drawable.ic_cat_education,
        "pets" to R.drawable.ic_cat_pets,
        "shopping" to R.drawable.ic_cat_shopping,
        "home" to R.drawable.ic_cat_home,
        "cafe" to R.drawable.ic_cat_cafe,
        "family" to R.drawable.ic_cat_family,
        "travel" to R.drawable.ic_cat_travel,
        "salary" to R.drawable.ic_cat_salary,
        "business" to R.drawable.ic_cat_business,
        "gift" to R.drawable.ic_cat_gift,
        "other" to R.drawable.ic_cat_other
    )

    /** Si la clave no existe, usa el ícono "other". */
    fun resId(key: String): Int = icons[key] ?: R.drawable.ic_cat_other
}