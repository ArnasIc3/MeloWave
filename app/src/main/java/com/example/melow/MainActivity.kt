package com.example.melow

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val username = intent.getStringExtra("username") ?: ""
        val userId   = intent.getLongExtra("userId", -1L)

        val profileButton = findViewById<Button>(R.id.profileButton)
        profileButton.text = if (username.isNotEmpty()) "◉  $username  ›" else "◉  Profile  ›"

        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).putExtra("username", username))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

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

        findViewById<TextView>(R.id.title).setOnClickListener { spawnNotes(it) }

        buttonSection.alpha = 0f
        buttonSection.translationY = 48f
        buttonSection.animate()
            .alpha(1f).translationY(0f)
            .setDuration(420).setStartDelay(220).start()

        findViewById<Button>(R.id.createButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, SecondActivity::class.java).putExtra("userId", userId))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        findViewById<Button>(R.id.loadButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, ProjectsActivity::class.java).putExtra("userId", userId))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        findViewById<Button>(R.id.arrangementButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, ArrangementActivity::class.java).putExtra("userId", userId))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        findViewById<Button>(R.id.soundLibraryButton).setOnClickListener {
            animateButton(it as Button) {
                startActivity(Intent(this, SoundLibraryActivity::class.java).putExtra("userId", userId))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    private fun spawnNotes(anchor: android.view.View) {
        val root = findViewById<ViewGroup>(R.id.mainScreen)
        val noteChars = listOf("♩", "♪", "♫", "♬", "♪", "♩", "♫", "♬", "♪", "♫", "♬", "♩")
        val colors = listOf(
            getColor(R.color.accent_purple), getColor(R.color.accent_cyan),
            getColor(R.color.accent_amber),  getColor(R.color.accent_green)
        )

        val anchorPos = IntArray(2).also { anchor.getLocationInWindow(it) }
        val rootPos   = IntArray(2).also { root.getLocationInWindow(it) }
        val originX   = (anchorPos[0] - rootPos[0] + anchor.width  / 2).toFloat()
        val originY   = (anchorPos[1] - rootPos[1] + anchor.height / 2).toFloat()

        noteChars.forEachIndexed { i, note ->
            Handler(Looper.getMainLooper()).postDelayed({
                val tv = TextView(this).apply {
                    text     = note
                    textSize = 18f + (Math.random() * 24).toFloat()
                    setTextColor(colors[i % colors.size])
                    alpha = 0f
                    x = originX + (Math.random() * 220 - 110).toFloat()
                    y = originY + (Math.random() * 40  - 20).toFloat()
                }
                root.addView(tv, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                tv.animate().alpha(1f).setDuration(120).withEndAction {
                    tv.animate()
                        .alpha(0f)
                        .translationX((Math.random() * 200 - 100).toFloat())
                        .translationY(-(90f + (Math.random() * 180).toFloat()))
                        .rotation((Math.random() * 60 - 30).toFloat())
                        .scaleX(0.4f).scaleY(0.4f)
                        .setDuration(800 + (Math.random() * 500).toLong())
                        .withEndAction { root.removeView(tv) }
                        .start()
                }.start()
            }, i * 65L)
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
