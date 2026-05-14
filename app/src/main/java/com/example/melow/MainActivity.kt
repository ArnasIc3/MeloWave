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
        logoSection.translationY = -40f
        logoSection.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500).setStartDelay(100).start()

        buttonSection.alpha = 0f
        buttonSection.translationY = 60f
        buttonSection.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500).setStartDelay(280).start()

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
        btn.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
            .withEndAction {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(80)
                    .withEndAction(action).start()
            }.start()
    }
}
