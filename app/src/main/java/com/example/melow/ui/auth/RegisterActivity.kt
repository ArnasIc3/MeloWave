package com.example.melow.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.melow.R
import com.example.melow.data.UserDbHelper
import com.example.melow.ui.main.MainActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var db: UserDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        db = UserDbHelper(this)

        val usernameEdit      = findViewById<EditText>(R.id.usernameEdit)
        val emailEdit         = findViewById<EditText>(R.id.emailEdit)
        val passwordEdit      = findViewById<EditText>(R.id.passwordEdit)
        val confirmPasswordEdit = findViewById<EditText>(R.id.confirmPasswordEdit)
        val errorLabel        = findViewById<TextView>(R.id.registerError)
        val createBtn         = findViewById<Button>(R.id.createAccountButton)
        val backBtn           = findViewById<Button>(R.id.backToLoginButton)
        val showPasswordBtn   = findViewById<Button>(R.id.showPasswordBtn)

        var passwordVisible = false
        showPasswordBtn.setOnClickListener {
            passwordVisible = !passwordVisible
            if (passwordVisible) {
                passwordEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                showPasswordBtn.text = "HIDE"
                showPasswordBtn.setTextColor(getColor(R.color.accent_purple))
            } else {
                passwordEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                showPasswordBtn.text = "SHOW"
                showPasswordBtn.setTextColor(getColor(R.color.text_muted))
            }
            passwordEdit.setSelection(passwordEdit.text.length)
        }

        val doRegister = {
            val username = usernameEdit.text.toString().trim()
            val email    = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString()
            val confirm  = confirmPasswordEdit.text.toString()

            val error = validate(username, email, password, confirm)
            if (error != null) {
                showError(errorLabel, error)
            } else {
                when (db.register(username, email, password)) {
                    UserDbHelper.RegisterResult.Success -> {
                        val result = db.login(email, password)
                        if (result is UserDbHelper.LoginResult.Success) {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("username", result.user.username)
                                putExtra("userId", result.user.id)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                        }
                    }
                    UserDbHelper.RegisterResult.EmailTaken     -> showError(errorLabel, "This email is already registered")
                    UserDbHelper.RegisterResult.EmailInvalid   -> showError(errorLabel, "Invalid email address")
                    UserDbHelper.RegisterResult.UsernameTooShort -> showError(errorLabel, "Username must be at least 2 characters")
                    UserDbHelper.RegisterResult.PasswordTooShort -> showError(errorLabel, "Password must be at least 6 characters")
                    UserDbHelper.RegisterResult.PasswordNoDigit  -> showError(errorLabel, "Password must contain at least one number")
                }
            }
        }

        createBtn.setOnClickListener { doRegister() }

        confirmPasswordEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { doRegister(); true } else false
        }

        backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun validate(username: String, email: String, password: String, confirm: String): String? {
        if (username.length < 2)    return "Username must be at least 2 characters"
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email address"
        if (password.length < 6)    return "Password must be at least 6 characters"
        if (password.none { it.isDigit() }) return "Password must contain at least one number"
        if (password != confirm)    return "Passwords don't match"
        return null
    }

    private fun showError(tv: TextView, msg: String) {
        tv.text = msg
        tv.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ tv.visibility = View.GONE }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }
}
