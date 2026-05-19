package com.example.fingo

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fingo.network.ApiClient
import com.example.fingo.network.TokenManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvGoRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0

        // Zaten giriş yapılmışsa direkt Piyasa'ya git
        if (TokenManager.isLoggedIn(this)) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)
        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        tilEmail       = findViewById(R.id.tilEmail)
        tilPassword    = findViewById(R.id.tilPassword)
        etEmail        = findViewById(R.id.etEmail)
        etPassword     = findViewById(R.id.etPassword)
        btnLogin       = findViewById(R.id.btnLogin)
        btnGoogle      = findViewById(R.id.btnGoogle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvGoRegister   = findViewById(R.id.tvGoRegister)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            if (validateInputs()) performLogin()
        }

        btnGoogle.setOnClickListener {
            Toast.makeText(this, "Google ile giriş yakında!", Toast.LENGTH_SHORT).show()
        }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Şifre sıfırlama e-postası gönderildi.", Toast.LENGTH_SHORT).show()
        }

        tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Geçerli bir e-posta adresi gerekli"
            isValid = false
        } else {
            tilEmail.error = null
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.error = "Şifre gerekli"
            isValid = false
        } else if (password.length < 6) {
            tilPassword.error = "Şifre en az 6 karakter olmalı"
            isValid = false
        } else {
            tilPassword.error = null
        }

        return isValid
    }

    /**
     * POST /api/v1/login/access-token
     * ⚠️ Form data (x-www-form-urlencoded) — JSON değil!
     */
    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.public.login(email, password)

                if (response.isSuccessful) {
                    val token = response.body()?.access_token
                    if (token != null) {
                        TokenManager.saveToken(this@LoginActivity, token)
                        TokenManager.saveUsername(this@LoginActivity, email)
                        Toast.makeText(this@LoginActivity, "Hoşgeldin! 🚀", Toast.LENGTH_SHORT).show()
                        goToMain()
                    } else {
                        showError("Sunucudan geçersiz yanıt alındı.")
                    }
                } else {
                    when (response.code()) {
                        400 -> showError("Kullanıcı adı veya şifre yanlış.")
                        403 -> showError("Erişim reddedildi.")
                        else -> showError("Hata: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                showError("Bağlantı hatası: ${e.localizedMessage}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = if (loading) "Giriş yapılıyor..." else getString(R.string.btn_login)
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
