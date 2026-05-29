package com.example.melow.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.melow.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val title = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        title.alpha = 0f
        title.scaleX = 0.88f
        title.scaleY = 0.88f
        title.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(550)
            .setInterpolator(DecelerateInterpolator())
            .start()

        subtitle.alpha = 0f
        subtitle.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(320)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 1600)
    }
}
