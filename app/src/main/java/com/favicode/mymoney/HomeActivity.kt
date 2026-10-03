package com.favicode.mymoney

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.favicode.mymoney.databinding.ActivityHomeBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setupToolbarAndDrawer()
        setupUserDataInDrawerHeader()
        setupNavigationDrawerMenu()
        setupChartFilters()
        setupBackNavigation()
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

        // Cambiar ícono del toggle para usar el tema verde azulado (Teal)
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
                    Toast.makeText(this, "Sección Metas (Alex)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_chatbot -> {
                    Toast.makeText(this, "Sección Chatbot (Fabri)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_budget -> {
                    Toast.makeText(this, "Sección Ingreso de Presupuesto/Gastos (Jose)", Toast.LENGTH_SHORT).show()
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
        // Callback para actualización interactiva al tocar la gráfica
        binding.financialChartView.onBarSelectedListener = { barData ->
            val netBalance = barData.income - barData.expense
            binding.tvTotalBalance.text = String.format("S/ %,.2f", netBalance)
            binding.tvTotalIncome.text = String.format("+ S/ %,.2f", barData.income)
            binding.tvTotalExpenses.text = String.format("- S/ %,.2f", barData.expense)
        }

        binding.btnFilterWeekly.setOnClickListener {
            updateButtonStyles(isWeekly = true, isMonthly = false, isYearly = false)
            val weeklyData = listOf(
                FinancialChartView.BarData("Lun", 800f, 350f),
                FinancialChartView.BarData("Mar", 1200f, 400f),
                FinancialChartView.BarData("Mié", 950f, 600f),
                FinancialChartView.BarData("Jue", 1500f, 200f),
                FinancialChartView.BarData("Vie", 1100f, 500f),
                FinancialChartView.BarData("Sáb", 450f, 700f),
                FinancialChartView.BarData("Dom", 500f, 250f)
            )
            binding.financialChartView.setChartData(weeklyData)
        }

        binding.btnFilterMonthly.setOnClickListener {
            updateButtonStyles(isWeekly = false, isMonthly = true, isYearly = false)
            val monthlyData = listOf(
                FinancialChartView.BarData("Ene", 4500f, 2100f),
                FinancialChartView.BarData("Feb", 5200f, 2800f),
                FinancialChartView.BarData("Mar", 4800f, 3100f),
                FinancialChartView.BarData("Abr", 6100f, 2400f),
                FinancialChartView.BarData("May", 5800f, 2900f),
                FinancialChartView.BarData("Jun", 6500f, 2250f)
            )
            binding.financialChartView.setChartData(monthlyData)
        }

        binding.btnFilterYearly.setOnClickListener {
            updateButtonStyles(isWeekly = false, isMonthly = false, isYearly = true)
            val yearlyData = listOf(
                FinancialChartView.BarData("2021", 45000f, 28000f),
                FinancialChartView.BarData("2022", 52000f, 32000f),
                FinancialChartView.BarData("2023", 68000f, 39000f),
                FinancialChartView.BarData("2024", 75000f, 41000f)
            )
            binding.financialChartView.setChartData(yearlyData)
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
