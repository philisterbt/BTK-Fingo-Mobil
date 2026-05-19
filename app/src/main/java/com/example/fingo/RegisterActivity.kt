package com.example.fingo

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fingo.network.ApiClient
import com.example.fingo.network.RegisterRequest
import com.example.fingo.network.TokenManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilPasswordConfirm: TextInputLayout
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etPasswordConfirm: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var tvGoLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_primary)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_register)
        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        tilFullName        = findViewById(R.id.tilFullName)
        tilEmail           = findViewById(R.id.tilEmail)
        tilPassword        = findViewById(R.id.tilPassword)
        tilPasswordConfirm = findViewById(R.id.tilPasswordConfirm)
        etFullName         = findViewById(R.id.etFullName)
        etEmail            = findViewById(R.id.etEmail)
        etPassword         = findViewById(R.id.etPassword)
        etPasswordConfirm  = findViewById(R.id.etPasswordConfirm)
        btnRegister        = findViewById(R.id.btnRegister)
        btnGoogle          = findViewById(R.id.btnGoogle)
        tvGoLogin          = findViewById(R.id.tvGoLogin)
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            if (validateInputs()) performRegister()
        }
        btnGoogle.setOnClickListener {
            Toast.makeText(this, "Google ile kayıt yakında!", Toast.LENGTH_SHORT).show()
        }
        tvGoLogin.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val email = etEmail.text.toString().trim()       // Backend artık email bekliyor
        val password = etPassword.text.toString().trim()
        val passwordConfirm = etPasswordConfirm.text.toString().trim()
        val fullName = etFullName.text.toString().trim()

        if (TextUtils.isEmpty(fullName) || fullName.length < 3) {
            tilFullName.error = "En az 3 karakter olmalı"
            isValid = false
        } else tilFullName.error = null

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Geçerli bir e-posta adresi giriniz"
            isValid = false
        } else tilEmail.error = null

        if (TextUtils.isEmpty(password) || password.length < 6) {
            tilPassword.error = "Şifre en az 6 karakter olmalı"
            isValid = false
        } else tilPassword.error = null

        if (password != passwordConfirm) {
            tilPasswordConfirm.error = "Şifreler eşleşmiyor"
            isValid = false
        } else tilPasswordConfirm.error = null

        return isValid
    }

    /**
     * POST /api/v1/register — JSON body
     * Backend: email + full_name + password (50.000 TL bakiye otomatik verilir)
     */
    private fun performRegister() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val fullName = etFullName.text.toString().trim()

        setLoading(true)

        lifecycleScope.launch {
            try {
                val body = RegisterRequest(email = email, full_name = fullName, password = password)
                val response = ApiClient.public.register(body)

                if (response.isSuccessful) {
                    val user = response.body()
                    Toast.makeText(
                        this@RegisterActivity,
                        "Hesabın oluşturuldu! $fullName artık 50.000 TL bakiyeyle başlıyor 🎉",
                        Toast.LENGTH_LONG
                    ).show()

                    // Kayıt sonrası otomatik giriş yap
                    autoLogin(email, password)

                } else {
                    when (response.code()) {
                        400 -> {
                            tilEmail.error = "Bu e-posta adresi zaten alınmış"
                        }
                        else -> Toast.makeText(this@RegisterActivity, "Hata: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                    setLoading(false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Bağlantı hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                setLoading(false)
            }
        }
    }

    /** Kayıt başarılıysa hemen giriş yap ve ana ekrana yönlendir */
    private suspend fun autoLogin(username: String, password: String) {
        try {
            val loginResponse = ApiClient.public.login(username, password)
            if (loginResponse.isSuccessful) {
                val token = loginResponse.body()?.access_token
                if (token != null) {
                    TokenManager.saveToken(this, token)
                    TokenManager.saveUsername(this, username)
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        } catch (e: Exception) {
            // Login başarısız olsa da kullanıcıya kayıt ekranına devam et
            setLoading(false)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        btnRegister.isEnabled = !loading
        btnRegister.text = if (loading) "Hesap oluşturuluyor..." else getString(R.string.btn_register)
    }
}
