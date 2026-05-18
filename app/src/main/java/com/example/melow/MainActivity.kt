package com.example.melow

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Show logged-in username
        val username = intent.getStringExtra("username") ?: ""
        if (username.isNotEmpty()) {
            findViewById<TextView>(R.id.usernameLabel).text = "Hi, $username"
        }

        // Logout
        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
                .edit().remove(LoginActivity.KEY_REMEMBERED).apply()
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }

        val logoSection = findViewById<LinearLayout>(R.id.logoSection)
        val buttonSection = findViewById<LinearLayout>(R.id.buttonSection)

        logoSection.alpha = 0f
        logoSection.translationY = -24f
        logoSection.animate()
            .alpha(1f).translationY(0f)
            .setDuration(420).setStartDelay(80).start()

        buttonSection.alpha = 0f
        buttonSection.translationY = 48f
        buttonSection.animate()
            .alpha(1f).translationY(0f)
            .setDuration(420).setStartDelay(220).start()

        findViewById<Button>(R.id.createButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, SecondActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        findViewById<Button>(R.id.loadButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, ProjectsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        findViewById<Button>(R.id.arrangementButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, ArrangementActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        findViewById<Button>(R.id.soundLibraryButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, SoundLibraryActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    private fun animateButton(btn: Button, action: () -> Unit) {
        btn.animate().scaleX(0.96f).scaleY(0.96f).setDuration(60)
            .withEndAction {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(100)
                    .withEndAction(action).start()
            }.start()
    }
}
