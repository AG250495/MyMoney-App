package com.favicode.mymoney

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Guarda presupuesto y movimientos en el teléfono (SharedPreferences), separados por usuario.
 *
 * Más adelante se puede cambiar por Firestore sin tocar la pantalla:
 * solo hay que mantener estas mismas funciones públicas.
 *
 * Barcita puede usar [getMonthSummary] para llenar el Menú Principal con datos reales.
 */
class BudgetRepository(context: Context, userId: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mymoney_budget_$userId", Context.MODE_PRIVATE)

    data class MonthSummary(val income: Double, val expense: Double) {
        val balance: Double get() = income - expense
    }

    // ---------- Movimientos ----------

    fun getAllTransactions(): List<Transaction> {
        val raw = prefs.getString(KEY_TRANSACTIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val list = ArrayList<Transaction>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Transaction(
                    id = o.getLong("id"),
                    type = Transaction.Type.valueOf(o.getString("type")),
                    amount = o.getDouble("amount"),
                    category = o.getString("category"),
                    description = o.optString("description", ""),
                    timestamp = o.getLong("timestamp")
                )
            )
        }
        return list
    }

    fun getTransactionsOfMonth(year: Int, month: Int): List<Transaction> =
        getAllTransactions()
            .filter { isInMonth(it.timestamp, year, month) }
            .sortedByDescending { it.timestamp }

    fun addTransaction(
        type: Transaction.Type,
        amount: Double,
        category: String,
        description: String,
        timestamp: Long
    ) {
        val list = getAllTransactions().toMutableList()
        list.add(
            Transaction(
                id = System.currentTimeMillis(),
                type = type,
                amount = amount,
                category = category,
                description = description,
                timestamp = timestamp
            )
        )
        save(list)
    }

    fun deleteTransaction(id: Long) {
        save(getAllTransactions().filterNot { it.id == id })
    }

    fun getMonthSummary(year: Int, month: Int): MonthSummary {
        val items = getTransactionsOfMonth(year, month)
        val income = items.filter { it.type == Transaction.Type.INCOME }.sumOf { it.amount }
        val expense = items.filter { it.type == Transaction.Type.EXPENSE }.sumOf { it.amount }
        return MonthSummary(income, expense)
    }
    /** Total gastado por categoría en un mes, de mayor a menor. */
    fun getExpenseTotalsByCategory(year: Int, month: Int): List<Pair<String, Double>> =
        getTransactionsOfMonth(year, month)
            .filter { it.type == Transaction.Type.EXPENSE }
            .groupBy { it.category }
            .map { (category, items) -> category to items.sumOf { it.amount } }
            .sortedByDescending { it.second }
    fun getSummaryBetween(startMillis: Long, endMillis: Long): MonthSummary {
        val items = getAllTransactions().filter { it.timestamp in startMillis until endMillis }
        val income = items.filter { it.type == Transaction.Type.INCOME }.sumOf { it.amount }
        val expense = items.filter { it.type == Transaction.Type.EXPENSE }.sumOf { it.amount }
        return MonthSummary(income, expense)
    }

    // ---------- Presupuesto mensual ----------

    /** [month] va de 0 (enero) a 11 (diciembre), igual que Calendar. */
    fun getBudget(year: Int, month: Int): Double =
        prefs.getFloat(budgetKey(year, month), 0f).toDouble()

    fun setBudget(year: Int, month: Int, amount: Double) {
        prefs.edit().putFloat(budgetKey(year, month), amount.toFloat()).apply()
    }

    // ---------- Internos ----------

    private fun save(list: List<Transaction>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type.name)
                    .put("amount", it.amount)
                    .put("category", it.category)
                    .put("description", it.description)
                    .put("timestamp", it.timestamp)
            )
        }
        prefs.edit().putString(KEY_TRANSACTIONS, array.toString()).apply()
    }

    private fun isInMonth(timestamp: Long, year: Int, month: Int): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
    }

    private fun budgetKey(year: Int, month: Int) = "budget_%d_%02d".format(year, month + 1)

    private companion object {
        const val KEY_TRANSACTIONS = "transactions"
            const val KEY_CATEGORIES = "categories"

    }
    // ---------- Categorías ----------

    fun getCategories(type: Transaction.Type): List<Category> =
        loadCategories().filter { it.type == type }

    /** Devuelve false si ya existe una categoría con ese nombre del mismo tipo. */
    fun addCategory(name: String, type: Transaction.Type, iconKey: String, colorHex: String): Boolean {
        val list = loadCategories().toMutableList()
        if (nameExists(list, name, type, excludeId = null)) return false
        list.add(Category(System.currentTimeMillis(), name.trim(), type, iconKey, colorHex))
        saveCategories(list)
        return true
    }

    /** Edita una categoría. Si cambia el nombre, también se renombra en sus movimientos. */
    fun updateCategory(id: Long, name: String, iconKey: String, colorHex: String): Boolean {
        val list = loadCategories().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index == -1) return false

        val old = list[index]
        if (nameExists(list, name, old.type, excludeId = id)) return false

        val updated = old.copy(name = name.trim(), iconKey = iconKey, colorHex = colorHex)
        list[index] = updated
        saveCategories(list)

        if (!old.name.equals(updated.name, ignoreCase = false)) {
            val renamed = getAllTransactions().map {
                if (it.type == old.type && it.category == old.name) it.copy(category = updated.name) else it
            }
            save(renamed)
        }
        return true
    }

    fun deleteCategory(id: Long) {
        saveCategories(loadCategories().filterNot { it.id == id })
    }

    private fun nameExists(list: List<Category>, name: String, type: Transaction.Type, excludeId: Long?) =
        list.any { it.type == type && it.id != excludeId && it.name.equals(name.trim(), ignoreCase = true) }

    private fun loadCategories(): List<Category> {
        // La primera vez se guardan las categorías por defecto
        if (!prefs.contains(KEY_CATEGORIES)) saveCategories(defaultCategories())

        val array = JSONArray(prefs.getString(KEY_CATEGORIES, "[]") ?: "[]")
        val list = ArrayList<Category>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Category(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = Transaction.Type.valueOf(o.getString("type")),
                    iconKey = o.getString("iconKey"),
                    colorHex = o.getString("colorHex")
                )
            )
        }
        return list
    }

    private fun saveCategories(list: List<Category>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("type", it.type.name)
                    .put("iconKey", it.iconKey)
                    .put("colorHex", it.colorHex)
            )
        }
        prefs.edit().putString(KEY_CATEGORIES, array.toString()).apply()
    }

    private fun defaultCategories(): List<Category> {
        val e = Transaction.Type.EXPENSE
        val i = Transaction.Type.INCOME
        return listOf(
            Category(1, "Alimentos", e, "food", "#00E676"),
            Category(2, "Servicios", e, "services", "#00D0B3"),
            Category(3, "Entretenimiento", e, "entertainment", "#FFB300"),
            Category(4, "Transporte", e, "transport", "#29B6F6"),
            Category(5, "Salud", e, "health", "#EF5350"),
            Category(6, "Educación", e, "education", "#AB47BC"),
            Category(7, "Otros", e, "other", "#8D6E63"),
            Category(8, "Sueldo", i, "salary", "#00E676"),
            Category(9, "Negocio", i, "business", "#00D0B3"),
            Category(10, "Regalo", i, "gift", "#FFB300"),
            Category(11, "Otros", i, "other", "#8D6E63")
        )
    }
}
