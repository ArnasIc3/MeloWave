package com.example.melow

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LoginActivity : AppCompatActivity() {

    companion object {
        const val PREFS_AUTH     = "auth"
        const val KEY_REMEMBERED = "remembered_username"
        const val KEY_REM_EMAIL  = "remembered_email"
    }

    private lateinit var db: UserDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = UserDbHelper(this)

        val prefs      = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE)
        val remembered = prefs.getString(KEY_REMEMBERED, null)
        if (!remembered.isNullOrEmpty()) {
            goToMain(remembered)
            return
        }

        setContentView(R.layout.activity_login)
        setupUI()
    }

    private fun setupUI() {
        val emailEdit    = findViewById<EditText>(R.id.emailEdit)
        val passwordEdit = findViewById<EditText>(R.id.passwordEdit)
        val rememberBox  = findViewById<CheckBox>(R.id.rememberCheck)
        val errorLabel   = findViewById<TextView>(R.id.loginError)
        val loginBtn     = findViewById<Button>(R.id.loginButton)
        val registerBtn  = findViewById<Button>(R.id.registerButton)
        val title        = findViewById<TextView>(R.id.appTitle)

        // Long-press title → developer DB viewer
        title.setOnLongClickListener {
            showDebugDialog()
            true
        }

        loginBtn.setOnClickListener {
            val email    = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString()

            val error = validateLoginInput(email, password)
            if (error != null) {
                showError(errorLabel, error)
                return@setOnClickListener
            }

            when (val result = db.login(email, password)) {
                is UserDbHelper.LoginResult.Success -> {
                    hideError(errorLabel)
                    if (rememberBox.isChecked) {
                        getSharedPreferences(PREFS_AUTH, MODE_PRIVATE).edit()
                            .putString(KEY_REMEMBERED, result.user.username)
                            .putString(KEY_REM_EMAIL,  result.user.email)
                            .apply()
                    }
                    goToMain(result.user.username)
                }
                UserDbHelper.LoginResult.NotFound    -> showError(errorLabel, "No account found with this email")
                UserDbHelper.LoginResult.WrongPassword -> showError(errorLabel, "Incorrect password")
            }
        }

        registerBtn.setOnClickListener { showRegisterDialog() }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateLoginInput(email: String, password: String): String? {
        if (email.isEmpty())    return "Email is required"
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email address"
        if (password.isEmpty()) return "Password is required"
        return null
    }

    private fun validateRegisterInput(
        username: String, email: String, password: String, confirm: String
    ): String? {
        if (username.trim().length < 2) return "Username must be at least 2 characters"
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email address"
        if (password.length < 6)        return "Password must be at least 6 characters"
        if (password.none { it.isDigit() }) return "Password must contain at least one number"
        if (password != confirm)        return "Passwords don't match"
        return null
    }

    // ── Register dialog ───────────────────────────────────────────────────────

    private fun showRegisterDialog() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), 0)
            setBackgroundColor(getColor(R.color.bg_surface))
        }

        fun field(hint: String, type: Int) = EditText(this).apply {
            this.hint = hint
            inputType = type
            setHintTextColor(getColor(R.color.text_muted))
            setTextColor(getColor(R.color.text_primary))
        }

        val userEdit  = field("Username (min 2 chars)",
            InputType.TYPE_CLASS_TEXT)
        val emailEdit = field("Email",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val passEdit  = field("Password (min 6 chars, 1 number)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val pass2Edit = field("Confirm password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val errorText = TextView(this).apply {
            setTextColor(getColor(R.color.button_danger))
            textSize = 12f
            visibility = View.GONE
        }

        for (v in listOf(userEdit, emailEdit, passEdit, pass2Edit, errorText)) {
            layout.addView(v, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10 * dp).toInt() })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(layout)
            .setPositiveButton("Register", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u  = userEdit.text.toString()
                val e  = emailEdit.text.toString().trim()
                val p  = passEdit.text.toString()
                val p2 = pass2Edit.text.toString()

                val err = validateRegisterInput(u, e, p, p2)
                if (err != null) {
                    errorText.text = err
                    errorText.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                when (db.register(u, e, p)) {
                    UserDbHelper.RegisterResult.Success -> {
                        dialog.dismiss()
                        Toast.makeText(this, "Account created! You can now log in.", Toast.LENGTH_SHORT).show()
                        findViewById<EditText>(R.id.emailEdit).setText(e)
                    }
                    UserDbHelper.RegisterResult.EmailTaken ->
                        { errorText.text = "This email is already registered"; errorText.visibility = View.VISIBLE }
                    UserDbHelper.RegisterResult.EmailInvalid ->
                        { errorText.text = "Invalid email address"; errorText.visibility = View.VISIBLE }
                    UserDbHelper.RegisterResult.UsernameTooShort ->
                        { errorText.text = "Username must be at least 2 characters"; errorText.visibility = View.VISIBLE }
                    UserDbHelper.RegisterResult.PasswordTooShort ->
                        { errorText.text = "Password must be at least 6 characters"; errorText.visibility = View.VISIBLE }
                    UserDbHelper.RegisterResult.PasswordNoDigit ->
                        { errorText.text = "Password must contain at least one number"; errorText.visibility = View.VISIBLE }
                }
            }
        }
        dialog.show()
    }

    // ── Debug viewer (long-press title) ───────────────────────────────────────

    private fun showDebugDialog() {
        val users = db.allUsers()
        val dbPath = db.dbFilePath(this)

        val sb = StringBuilder()
        sb.appendLine("DB: $dbPath")
        sb.appendLine("─".repeat(30))
        if (users.isEmpty()) {
            sb.appendLine("(no users registered)")
        } else {
            users.forEach { (id, name, email) ->
                sb.appendLine("#$id  $name")
                sb.appendLine("     $email")
            }
        }
        sb.appendLine()
        sb.appendLine("ADB pull command:")
        sb.appendLine("adb exec-out run-as com.example.melow cat databases/melowave_users.db > melowave_users.db")

        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = sb.toString()
            textSize = 11f
            setTextColor(getColor(R.color.text_secondary))
            setBackgroundColor(getColor(R.color.bg_surface))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            typeface = android.graphics.Typeface.MONOSPACE
        }

        AlertDialog.Builder(this)
            .setTitle("Dev — Database")
            .setView(tv)
            .setPositiveButton("Close", null)
            .setNeutralButton("Export to Downloads") { _, _ -> exportDbToDownloads(dbPath) }
            .show()
    }

    private fun exportDbToDownloads(dbPath: String) {
        try {
            val src = File(dbPath)
            val dst = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "melowave_users.db"
            )
            src.copyTo(dst, overwrite = true)
            Toast.makeText(this, "Exported to Downloads/melowave_users.db", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun goToMain(username: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("username", username)
            // Clear back stack — back button from MainActivity cannot return to Login
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showError(tv: TextView, msg: String) {
        tv.text = msg
        tv.visibility = View.VISIBLE
    }

    private fun hideError(tv: TextView) {
        tv.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }
}
