package com.favicode.mymoney

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var txtEmail: EditText
    private lateinit var txtPass: EditText
    private lateinit var buttonLogin: MaterialButton
    private lateinit var textViewRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        txtEmail = findViewById(R.id.txtEmail)
        txtPass = findViewById(R.id.txtPass)
        buttonLogin = findViewById(R.id.btnLogin)
        textViewRegister = findViewById(R.id.textViewRegister)

        buttonLogin.setOnClickListener {
            val email = txtEmail.text?.toString()?.trim() ?: ""
            val password = txtPass.text?.toString()?.trim() ?: ""

            if (email.isNotEmpty() && password.isNotEmpty()) {
                login(email, password)
            } else {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        textViewRegister.setOnClickListener {
            goToRegister()
        }
    }

    private fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val intent = Intent(this, HomeActivity::class.java)
                    startActivity(intent)
                    finishAffinity()
                } else {
                    Toast.makeText(
                        this,
                        "Error al ingresar: ${task.exception?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun goToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }
}
