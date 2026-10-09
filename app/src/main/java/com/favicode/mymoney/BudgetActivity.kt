package com.favicode.mymoney

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.favicode.mymoney.databinding.ActivityBudgetBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.TimeZone
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
/**
 * Pantalla "Ingreso de presupuesto / gastos mensuales" (parte de Jose).
 *
 * - Define el presupuesto del mes.
 * - Registra ingresos y gastos con categoría.
 * - Muestra cuánto del presupuesto ya se gastó.
 */
class BudgetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBudgetBinding
    private lateinit var repository: BudgetRepository
    private lateinit var adapter: TransactionAdapter

    /** Mes que se está viendo (siempre día 1). */
    private val shownMonth: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }

    private var selectedType = Transaction.Type.EXPENSE

    private var editingOriginal: Transaction? = null

    private var selectedDateMillis: Long = System.currentTimeMillis()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PE"))

    private var categoryMap: Map<Pair<Transaction.Type, String>, Category> = emptyMap()

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "PE"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "invitado"
        repository = BudgetRepository(this, userId)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupList()
        setupMonthSelector()
        setupBudgetSection()
        setupNewTransactionSection()
        setupDateField()

        loadMonth(updateBudgetField = true)
    }
    override fun onResume() {
        super.onResume()
        setCategoryAdapter()
        loadMonth(updateBudgetField = false)
        adapter.notifyDataSetChanged()
    }
    // ---------- Configuración de vistas ----------

    private fun setupList() {
        adapter = TransactionAdapter(
            categoryOf = { categoryMap[it.type to it.category] },
            onItemClick = { startEditing(it) },
            onDeleteClick = { confirmDelete(it) }
        )
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun setupMonthSelector() {
        binding.btnPrevMonth.setOnClickListener {
            shownMonth.add(Calendar.MONTH, -1)
            syncDateToShownMonth()
            loadMonth(updateBudgetField = true)
        }
        binding.btnNextMonth.setOnClickListener {
            shownMonth.add(Calendar.MONTH, 1)
            syncDateToShownMonth()
            loadMonth(updateBudgetField = true)
        }
        // Solo si ya hiciste el Paso B:
        binding.tvMonth.setOnClickListener { showMonthPicker() }
    }

    private fun setupBudgetSection() {
        binding.btnSaveBudget.setOnClickListener {
            val amount = parseAmount(binding.etBudget.text?.toString())
            if (amount == null || amount < 0) {
                binding.tilBudget.error = getString(R.string.budget_error_amount)
                return@setOnClickListener
            }
            binding.tilBudget.error = null
            repository.setBudget(shownMonth.get(Calendar.YEAR), shownMonth.get(Calendar.MONTH), amount)
            hideKeyboard()
            Toast.makeText(this, R.string.budget_saved, Toast.LENGTH_SHORT).show()
            loadMonth(updateBudgetField = false)
        }
    }

    private fun setupNewTransactionSection() {
        binding.btnTypeExpense.setOnClickListener { selectType(Transaction.Type.EXPENSE) }
        binding.btnTypeIncome.setOnClickListener { selectType(Transaction.Type.INCOME) }
        selectType(Transaction.Type.EXPENSE)

        binding.btnAddTransaction.setOnClickListener { addTransaction() }
        binding.btnManageCategories.setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }
        binding.btnCancelEdit.setOnClickListener { stopEditing() }
    }
    private fun startEditing(item: Transaction) {
        editingOriginal = item

        selectType(item.type)              // cambia Gasto/Ingreso y las categorías
        selectedDateMillis = item.timestamp
        updateDateField()

        binding.etAmount.setText(formatPlain(item.amount))
        binding.actCategory.setText(item.category, false)
        updateCategoryIcon(item.category)
        binding.etDescription.setText(item.description)
        binding.tilAmount.error = null
        binding.tilCategory.error = null

        binding.tvFormTitle.setText(R.string.budget_edit_title)
        binding.btnAddTransaction.setText(R.string.budget_save_changes)
        binding.btnCancelEdit.visibility = View.VISIBLE

        // Llevar la vista al formulario
        binding.scrollBudget.post {
            binding.scrollBudget.smoothScrollTo(0, binding.cardForm.top)
        }
    }

    private fun stopEditing() {
        editingOriginal = null

        binding.tvFormTitle.setText(R.string.budget_new_title)
        binding.btnAddTransaction.setText(R.string.budget_add)
        binding.btnCancelEdit.visibility = View.GONE

        binding.etAmount.setText("")
        binding.etDescription.setText("")
        binding.tilAmount.error = null
        binding.tilCategory.error = null
        clearCategorySelection()
    }

    private fun isSameMonth(millis: Long, year: Int, month: Int): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
    }

    private fun selectType(type: Transaction.Type) {
        selectedType = type

        styleTypeButton(binding.btnTypeExpense, type == Transaction.Type.EXPENSE, R.color.expense_red, R.color.text_primary)
        styleTypeButton(binding.btnTypeIncome, type == Transaction.Type.INCOME, R.color.income_green, R.color.bg_dark)

        setCategoryAdapter()
        clearCategorySelection()
        binding.tilCategory.error = null

    }

    private fun setCategoryAdapter() {
        val categories = repository.getCategories(selectedType)
        binding.actCategory.setAdapter(CategoryDropdownAdapter(this, categories))
        binding.actCategory.setDropDownBackgroundResource(R.color.card_dark_secondary)

        // Al elegir una opción, mostrar su ícono dentro del campo
        binding.actCategory.setOnItemClickListener { parent, _, position, _ ->
            updateCategoryIcon(parent.getItemAtPosition(position) as String)
        }
        // Por si la categoría elegida cambió de color o ícono mientras estabas en otra pantalla
        updateCategoryIcon(binding.actCategory.text?.toString().orEmpty())
    }
    /** Pone (o quita) el ícono de la categoría dentro del campo. */
    private fun updateCategoryIcon(name: String) {
        val category = repository.getCategories(selectedType).firstOrNull { it.name == name }
        if (category == null) {
            binding.tilCategory.startIconDrawable = null
            return
        }
        binding.tilCategory.setStartIconDrawable(CategoryIcons.resId(category.iconKey))
        binding.tilCategory.setStartIconTintList(ColorStateList.valueOf(Color.parseColor(category.colorHex)))
    }

    private fun clearCategorySelection() {
        binding.actCategory.setText("", false)
        updateCategoryIcon("")
    }

    private fun styleTypeButton(button: MaterialButton, active: Boolean, activeColor: Int, activeTextColor: Int) {
        if (active) {
            button.setBackgroundColor(ContextCompat.getColor(this, activeColor))
            button.setTextColor(ContextCompat.getColor(this, activeTextColor))
        } else {
            button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    // ---------- Acciones ----------

    private fun addTransaction() {
        val amount = parseAmount(binding.etAmount.text?.toString())
        val category = binding.actCategory.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()

        var valid = true
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = getString(R.string.budget_error_amount)
            valid = false
        } else {
            binding.tilAmount.error = null
        }
        if (category.isEmpty()) {
            binding.tilCategory.error = getString(R.string.budget_error_category)
            valid = false
        } else {
            binding.tilCategory.error = null
        }
        if (!valid || amount == null) return

        val dateCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val year = dateCal.get(Calendar.YEAR)
        val month = dateCal.get(Calendar.MONTH)
        val original = editingOriginal

        // No gastar más de lo que hay disponible en el mes de la fecha elegida
        if (selectedType == Transaction.Type.EXPENSE) {
            var available = repository.getMonthSummary(year, month).balance

            // Si estamos editando, el movimiento original no cuenta (se va a reemplazar)
            if (original != null && isSameMonth(original.timestamp, year, month)) {
                available += if (original.type == Transaction.Type.EXPENSE) original.amount else -original.amount
            }

            if (amount > available) {
                binding.tilAmount.error = getString(
                    R.string.budget_error_insufficient,
                    money(available.coerceAtLeast(0.0))
                )
                return
            }
        }

        if (original == null) {
            repository.addTransaction(selectedType, amount, category, description, selectedDateMillis)
            val msg = if (selectedType == Transaction.Type.INCOME) R.string.budget_income_added else R.string.budget_expense_added
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } else {
            repository.updateTransaction(original.id, selectedType, amount, category, description, selectedDateMillis)
            Toast.makeText(this, R.string.budget_updated, Toast.LENGTH_SHORT).show()
        }

        stopEditing()
        hideKeyboard()

        // Mostrar el mes donde quedó guardado
        shownMonth.set(year, month, 1)
        loadMonth(updateBudgetField = true)
    }
    private fun confirmDelete(item: Transaction) {
        AlertDialog.Builder(this)
            .setTitle(R.string.budget_delete_title)
            .setMessage(getString(R.string.budget_delete_message, item.category))
            .setPositiveButton(R.string.budget_delete_confirm) { _, _ ->
                if (editingOriginal?.id == item.id) stopEditing()
                repository.deleteTransaction(item.id)
                loadMonth(updateBudgetField = false)
            }
            .setNegativeButton(R.string.budget_cancel, null)
            .show()
    }

    // ---------- Refrescar pantalla ----------

    private fun loadMonth(updateBudgetField: Boolean) {refreshCategoryMap()
        val year = shownMonth.get(Calendar.YEAR)
        val month = shownMonth.get(Calendar.MONTH)

        binding.tvMonth.text = monthFormat.format(shownMonth.time)
            .replaceFirstChar { it.uppercase() }

        val transactions = repository.getTransactionsOfMonth(year, month)
        val summary = repository.getMonthSummary(year, month)
        val budget = repository.getBudget(year, month)

        // Resumen del mes
        binding.tvMonthIncome.text = money(summary.income)
        binding.tvMonthExpense.text = money(summary.expense)
        binding.tvMonthBalance.text = money(summary.balance)
        binding.tvMonthBalance.setTextColor(
            ContextCompat.getColor(this, if (summary.balance >= 0) R.color.text_primary else R.color.expense_red)
        )

        // Presupuesto
        if (updateBudgetField) {
            binding.etBudget.setText(if (budget > 0) formatPlain(budget) else "")
            binding.tilBudget.error = null
        }
        updateBudgetProgress(budget, summary.expense)

        // Lista
        adapter.submitList(transactions)
        binding.tvEmptyList.visibility = if (transactions.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.rvTransactions.visibility = if (transactions.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateBudgetProgress(budget: Double, spent: Double) {
        if (budget <= 0) {
            binding.progressBudget.progress = 0
            binding.tvBudgetStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvBudgetStatus.text = getString(R.string.budget_not_set)
            return
        }

        val percent = (spent / budget * 100).toInt()
        binding.progressBudget.setProgressCompat(percent.coerceIn(0, 100), true)

        val colorRes = when {
            percent >= 100 -> R.color.expense_red
            percent >= 80 -> R.color.budget_warning
            else -> R.color.income_green
        }
        binding.progressBudget.setIndicatorColor(ContextCompat.getColor(this, colorRes))

        val remaining = budget - spent
        binding.tvBudgetStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvBudgetStatus.text = if (remaining >= 0) {
            getString(R.string.budget_status_ok, money(spent), money(budget), percent, money(remaining))
        } else {
            getString(R.string.budget_status_over, money(spent), money(budget), money(-remaining))
        }
    }

    // ---------- Utilidades ----------

    /** Si el mes mostrado es el actual usa la hora de ahora; si no, el día 1 de ese mes. */
    private fun timestampForShownMonth(): Long {
        val now = Calendar.getInstance()
        val sameMonth = now.get(Calendar.YEAR) == shownMonth.get(Calendar.YEAR) &&
                now.get(Calendar.MONTH) == shownMonth.get(Calendar.MONTH)
        if (sameMonth) return now.timeInMillis

        return (shownMonth.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
    }

    private fun parseAmount(text: String?): Double? =
        text?.trim()?.replace(",", ".")?.toDoubleOrNull()

    private fun money(value: Double): String = String.format(Locale.US, "S/ %,.2f", value)

    private fun formatPlain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
    private fun setupDateField() {
        updateDateField()
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }
    }

    private fun updateDateField() {
        binding.etDate.setText(dateFormat.format(selectedDateMillis))
    }

    private fun showDatePicker() {
        // El calendario trabaja en UTC, por eso convertimos al abrir y al cerrar
        val local = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val utcSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.budget_date_picker_title)
            .setSelection(utcSelection)
            .build()

        picker.addOnPositiveButtonClickListener { utcMillis ->
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
            val now = Calendar.getInstance()
            // Día elegido + hora actual, para que los movimientos del mismo día queden en orden
            selectedDateMillis = Calendar.getInstance().apply {
                set(
                    utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)
                )
            }.timeInMillis
            updateDateField()
// Llevar la pantalla al mes de la fecha elegida
            shownMonth.set(
                Calendar.getInstance().apply { timeInMillis = selectedDateMillis }.get(Calendar.YEAR),
                Calendar.getInstance().apply { timeInMillis = selectedDateMillis }.get(Calendar.MONTH),
                1
            )
            loadMonth(updateBudgetField = true)
        }
        picker.show(supportFragmentManager, "date_picker")
    }
    private fun showMonthPicker() {
        // Abre el calendario en el mes que se está viendo (el calendario usa UTC)
        val utcSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(shownMonth.get(Calendar.YEAR), shownMonth.get(Calendar.MONTH), 1)
        }.timeInMillis

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.budget_month_picker_title)
            .setSelection(utcSelection)
            .build()

        picker.addOnPositiveButtonClickListener { utcMillis ->
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
            // Del día elegido solo nos importan el año y el mes
            shownMonth.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), 1)
            syncDateToShownMonth()
            loadMonth(updateBudgetField = true)
        }
        picker.show(supportFragmentManager, "month_picker")
    }
    /** Mueve la fecha del formulario al mes que se está viendo, conservando el día si existe. */
    private fun syncDateToShownMonth() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val day = cal.get(Calendar.DAY_OF_MONTH)

        cal.set(Calendar.DAY_OF_MONTH, 1) // evita desbordes (31 de enero -> febrero)
        cal.set(Calendar.YEAR, shownMonth.get(Calendar.YEAR))
        cal.set(Calendar.MONTH, shownMonth.get(Calendar.MONTH))
        cal.set(Calendar.DAY_OF_MONTH, minOf(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))

        selectedDateMillis = cal.timeInMillis
        updateDateField()
    }private fun refreshCategoryMap() {
        categoryMap = (repository.getCategories(Transaction.Type.EXPENSE) +
                repository.getCategories(Transaction.Type.INCOME))
            .associateBy { it.type to it.name }
    }
}
