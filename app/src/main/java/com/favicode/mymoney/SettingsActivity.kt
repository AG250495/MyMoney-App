package com.favicode.mymoney

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnResetPassword: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private lateinit var imgProfile: ImageView
    private lateinit var tvInitials: TextView

    private lateinit var spCurrency: AutoCompleteTextView

    private lateinit var tvVersion: TextView
    private lateinit var btnAbout: View
    private lateinit var btnPrivacy: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            startActivity(Intent(this, Wellcome::class.java))
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Referencias
        imgProfile = findViewById(R.id.imgProfile)
        tvInitials = findViewById(R.id.tvInitials)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        btnSave = findViewById(R.id.btnSave)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        btnLogout = findViewById(R.id.btnLogout)

        spCurrency = findViewById(R.id.spCurrency)

        tvVersion = findViewById(R.id.tvVersion)
        btnAbout = findViewById(R.id.btnAbout)
        btnPrivacy = findViewById(R.id.btnPrivacy)

        // Cargar datos del perfil
        setupUserProfile()

        // Configurar dropdown de moneda
        setupPreferences()

        // Configurar versión, acerca de y privacidad
        setupInfoAndSupport()

        // Botón Guardar Nombre
        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateProfile(newName)
            } else {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón Restablecer Contraseña
        btnResetPassword.setOnClickListener {
            resetPassword()
        }

        // Botón Cerrar Sesión
        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun setupUserProfile() {
        val user = auth.currentUser ?: return

        val email = user.email ?: "Sin correo registrado"
        val displayName = user.displayName ?: if (email.contains("@")) email.substringBefore("@") else "Usuario"

        etEmail.setText(email)
        etName.setText(displayName)

        // Foto de perfil o iniciales
        val photoUrl = user.photoUrl
        if (photoUrl != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val stream = URL(photoUrl.toString()).openStream()
                    val bitmap = BitmapFactory.decodeStream(stream)
                    withContext(Dispatchers.Main) {
                        imgProfile.setImageBitmap(bitmap)
                        imgProfile.visibility = View.VISIBLE
                        tvInitials.visibility = View.GONE
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        showInitials(displayName)
                    }
                }
            }
        } else {
            showInitials(displayName)
        }
    }

    private fun showInitials(name: String) {
        imgProfile.visibility = View.GONE
        tvInitials.visibility = View.VISIBLE
        val initial = name.trim().take(1).uppercase()
        tvInitials.text = initial.ifEmpty { "U" }
    }

    private fun updateProfile(newName: String) {
        val user = auth.currentUser ?: return

        val profileUpdates = userProfileChangeRequest {
            displayName = newName
        }

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                    showInitials(newName)
                } else {
                    Toast.makeText(this, "Error al actualizar: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun resetPassword() {
        val email = auth.currentUser?.email
        if (!email.isNullOrEmpty()) {
            auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Se ha enviado un correo a $email para restablecer la contraseña",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Error al enviar correo: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(this, "No se encontró un correo válido para esta cuenta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPreferences() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Opciones de moneda ampliadas con USD como predeterminado
        val currencyOptions = arrayOf(
            "$ (Dólar estadounidense - USD)",
            "S/ (Sol peruano - PEN)",
            "€ (Euro - EUR)",
            "MXN$ (Peso mexicano - MXN)",
            "COP$ (Peso colombiano - COP)",
            "R$ (Real brasileño - BRL)",
            "CLP$ (Peso chileno - CLP)",
            "ARS$ (Peso argentino - ARS)"
        )
        val currencyAdapter = ArrayAdapter(this, R.layout.item_dropdown, currencyOptions)
        spCurrency.setAdapter(currencyAdapter)

        val defaultCurrency = currencyOptions[0] // Dólar estadounidense por defecto
        val savedCurrency = prefs.getString("currency_symbol", defaultCurrency) ?: defaultCurrency
        spCurrency.setText(savedCurrency, false)

        spCurrency.setOnItemClickListener { _, _, position, _ ->
            val selectedCurrency = currencyOptions[position]
            prefs.edit().putString("currency_symbol", selectedCurrency).apply()
            Toast.makeText(this, "Moneda guardada: $selectedCurrency", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInfoAndSupport() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "Versión ${pInfo.versionName}"
        } catch (_: Exception) {
            tvVersion.text = "Versión 1.0.0"
        }

        btnAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Acerca de MyMoney")
                .setMessage(
                    "MyMoney v1.0.0\n\n" +
                            "Aplicación de gestión de finanzas personales, presupuestos y control de gastos.\n\n" +
                            "Desarrollado con Jetpack Android, Firebase y SQLite."
                )
                .setPositiveButton("Entendido", null)
                .show()
        }

        btnPrivacy.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Términos y Privacidad")
                .setMessage(
                    "En MyMoney respetamos tu privacidad.\n\n" +
                            "• Tus transacciones y presupuestos se almacenan localmente de forma privada en tu dispositivo.\n" +
                            "• Tu información de cuenta es gestionada mediante autenticación segura con Google Firebase.\n" +
                            "• No compartimos tus datos con terceros."
                )
                .setPositiveButton("Cerrar", null)
                .show()
        }
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
