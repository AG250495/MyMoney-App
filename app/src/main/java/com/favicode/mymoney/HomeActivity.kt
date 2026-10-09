package com.favicode.mymoney

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.favicode.mymoney.databinding.ActivityHomeBinding
import com.favicode.mymoney.databinding.ItemCategoryRowBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth

    private lateinit var repository: BudgetRepository
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        repository = BudgetRepository(this, auth.currentUser?.uid ?: "invitado")
        dbHelper = DatabaseHelper(this)

        setupToolbarAndDrawer()
        setupUserDataInDrawerHeader()
        setupNavigationDrawerMenu()
        setupChartFilters()
        setupBackNavigation()
        setupSavingsGoal()
    }

    override fun onResume() {
        super.onResume()
        updateButtonStyles(isWeekly = false, isMonthly = true, isYearly = false)
        applyChartData(buildMonthlyData())
        showCategories()
        loadSavingsGoal()
    }

    private fun setupSavingsGoal() {
        binding.btnSetGoal.setOnClickListener {
            showSetGoalDialog()
        }
    }

    private fun getCurrentMonthInfo(): Triple<String, Int, String> {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM", Locale("es", "ES"))
        val fullDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val rawMonth = monthFormat.format(calendar.time)
        val monthName = rawMonth.replaceFirstChar { it.uppercase() }
        val year = calendar.get(Calendar.YEAR)
        val currentDate = fullDateFormat.format(calendar.time)

        return Triple(monthName, year, currentDate)
    }

    private fun loadSavingsGoal() {
        val (monthName, year, _) = getCurrentMonthInfo()
        val goal = dbHelper.getGoalForMonth(monthName, year)

        binding.tvGoalTitle.text = "Meta del mes de $monthName"

        if (goal != null) {
            binding.tvGoalAmount.text = String.format(Locale.US, "$ %.2f", goal.amount)
            binding.tvGoalDate.text = "Fijada el: ${goal.createdAt}"
            binding.btnSetGoal.text = "Modificar"
        } else {
            binding.tvGoalAmount.text = "$ 0.00"
            binding.tvGoalDate.text = "Aún no has fijado una meta para este mes"
            binding.btnSetGoal.text = "Definir meta"
        }
    }

    private fun showSetGoalDialog() {
        val (monthName, year, currentDate) = getCurrentMonthInfo()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_goal, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etGoalAmount)
        val tvPrompt = dialogView.findViewById<TextView>(R.id.tvDialogPrompt)

        tvPrompt.text = "¿Cuánto dinero quieres ahorrar este mes de $monthName?"

        AlertDialog.Builder(this)
            .setTitle("Meta de $monthName")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, _ ->
                val amountText = etAmount.text.toString().trim()
                if (amountText.isNotEmpty()) {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        dbHelper.saveOrUpdateGoal(amount, monthName, year, currentDate)
                        Toast.makeText(this, "Meta guardada con éxito", Toast.LENGTH_SHORT).show()
                        loadSavingsGoal()
                    } else {
                        Toast.makeText(this, "Por favor ingresa un monto válido", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showCategories() {
        val now = Calendar.getInstance()
        val totals = repository.getExpenseTotalsByCategory(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        val grandTotal = totals.sumOf { it.second }
        val palette = listOf("#00E676", "#00D0B3", "#FFB300", "#29B6F6", "#AB47BC", "#FF7043", "#8D6E63")

        binding.llCategories.removeAllViews()
        binding.tvNoCategories.visibility = if (totals.isEmpty()) View.VISIBLE else View.GONE

        totals.forEachIndexed { index, (name, amount) ->
            val percent = if (grandTotal > 0) (amount / grandTotal * 100).toInt() else 0
            val row = ItemCategoryRowBinding.inflate(layoutInflater, binding.llCategories, false)
            row.tvCategoryName.text = name
            row.tvCategoryValue.text = String.format(Locale.US, "S/ %,.2f (%d%%)", amount, percent)
            row.progressCategory.progress = percent
            row.progressCategory.setIndicatorColor(Color.parseColor(palette[index % palette.size]))
            binding.llCategories.addView(row.root)
        }
    }

    private fun applyChartData(data: List<FinancialChartView.BarData>) {
        binding.financialChartView.setChartData(data)
        data.lastOrNull()?.let { showSummary(it.income, it.expense) }
    }

    private fun showSummary(income: Float, expense: Float) {
        binding.tvTotalBalance.text = String.format(Locale.US, "S/ %,.2f", income - expense)
        binding.tvTotalIncome.text = String.format(Locale.US, "+ S/ %,.2f", income)
        binding.tvTotalExpenses.text = String.format(Locale.US, "- S/ %,.2f", expense)
    }

    private fun buildMonthlyData(): List<FinancialChartView.BarData> {
        val labelFormat = SimpleDateFormat("MMM", Locale("es", "PE"))
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -5)
        }
        val list = mutableListOf<FinancialChartView.BarData>()
        repeat(6) {
            val s = repository.getMonthSummary(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            val label = labelFormat.format(cal.time).replace(".", "").take(3)
                .replaceFirstChar { it.uppercase() }
            list.add(FinancialChartView.BarData(label, s.income.toFloat(), s.expense.toFloat()))
            cal.add(Calendar.MONTH, 1)
        }
        return list
    }

    private fun buildWeeklyData(): List<FinancialChartView.BarData> {
        val labelFormat = SimpleDateFormat("EEE", Locale("es", "PE"))
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val list = mutableListOf<FinancialChartView.BarData>()
        repeat(7) {
            val start = cal.timeInMillis
            val label = labelFormat.format(cal.time).replace(".", "").take(3)
                .replaceFirstChar { it.uppercase() }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val s = repository.getSummaryBetween(start, cal.timeInMillis)
            list.add(FinancialChartView.BarData(label, s.income.toFloat(), s.expense.toFloat()))
        }
        return list
    }

    private fun buildYearlyData(): List<FinancialChartView.BarData> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return (currentYear - 3..currentYear).map { year ->
            val start = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1) }.timeInMillis
            val end = Calendar.getInstance().apply { clear(); set(year + 1, Calendar.JANUARY, 1) }.timeInMillis
            val s = repository.getSummaryBetween(start, end)
            FinancialChartView.BarData(year.toString(), s.income.toFloat(), s.expense.toFloat())
        }
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)

        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, R.color.accent_teal)
    }

    private fun setupUserDataInDrawerHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvHeaderEmail = headerView.findViewById<TextView>(R.id.tvNavHeaderEmail)
        val tvHeaderName = headerView.findViewById<TextView>(R.id.tvNavHeaderName)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val email = currentUser.email ?: "Sin correo registrado"
            tvHeaderEmail.text = email
            val displayName = currentUser.displayName ?: email.substringBefore("@")
            tvHeaderName.text = displayName
        } else {
            tvHeaderName.text = getString(R.string.app_name)
            tvHeaderEmail.text = getString(R.string.nav_header_default_email)
        }
    }

    private fun setupNavigationDrawerMenu() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)

            when (menuItem.itemId) {
                R.id.nav_home -> {
                    Toast.makeText(this, "Menú principal (Barcita)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_goals -> {
                    showSetGoalDialog()
                    true
                }
                R.id.nav_chatbot -> {
                    Toast.makeText(this, "Sección Chatbot (Fabri)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Sección Ajustes (Artu)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupChartFilters() {
        binding.financialChartView.onBarSelectedListener = { barData ->
            val netBalance = barData.income - barData.expense
            binding.tvTotalBalance.text = String.format("S/ %,.2f", netBalance)
            binding.tvTotalIncome.text = String.format("+ S/ %,.2f", barData.income)
            binding.tvTotalExpenses.text = String.format("- S/ %,.2f", barData.expense)
        }

        binding.btnFilterWeekly.setOnClickListener {
            updateButtonStyles(isWeekly = true, isMonthly = false, isYearly = false)
            applyChartData(buildWeeklyData())
        }

        binding.btnFilterMonthly.setOnClickListener {
            updateButtonStyles(isWeekly = false, isMonthly = true, isYearly = false)
            applyChartData(buildMonthlyData())
        }

        binding.btnFilterYearly.setOnClickListener {
            updateButtonStyles(isWeekly = false, isMonthly = false, isYearly = true)
            applyChartData(buildYearlyData())
        }
    }

    private fun updateButtonStyles(isWeekly: Boolean, isMonthly: Boolean, isYearly: Boolean) {
        val tealColor = ContextCompat.getColor(this, R.color.accent_teal)
        val bgDarkColor = ContextCompat.getColor(this, R.color.bg_dark)
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)

        fun applyStyle(btn: com.google.android.material.button.MaterialButton, isActive: Boolean) {
            if (isActive) {
                btn.setBackgroundColor(tealColor)
                btn.setTextColor(bgDarkColor)
            } else {
                btn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                btn.setTextColor(textSecondary)
            }
        }

        applyStyle(binding.btnFilterWeekly, isWeekly)
        applyStyle(binding.btnFilterMonthly, isMonthly)
        applyStyle(binding.btnFilterYearly, isYearly)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun logout() {
        auth.signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, Wellcome::class.java)
            startActivity(intent)
            finishAffinity()
        }
    }
}