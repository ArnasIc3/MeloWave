package com.example.melow.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.melow.R
import com.example.melow.data.Achievement
import com.example.melow.data.AchievementManager

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val username = intent.getStringExtra("username") ?: ""
        val unlocked = AchievementManager.unlockedCount(this)

        findViewById<TextView>(R.id.profileUsername).text = username
        findViewById<TextView>(R.id.achievementCount).text = "$unlocked / ${AchievementManager.all.size} achievements"
        findViewById<ProgressBar>(R.id.achievementProgress).progress = unlocked

        val recycler = findViewById<RecyclerView>(R.id.achievementsRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = AchievementAdapter(AchievementManager.all.map {
            it to AchievementManager.isUnlocked(this, it.id)
        })

        findViewById<android.widget.Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private inner class AchievementAdapter(
        private val items: List<Pair<Achievement, Boolean>>
    ) : RecyclerView.Adapter<AchievementAdapter.VH>() {

        private val icons = listOf("★", "↑", "◈", "♪", "▣", "⚡", "~")

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon   = view.findViewById<TextView>(R.id.achievementIcon)
            val title  = view.findViewById<TextView>(R.id.achievementTitle)
            val desc   = view.findViewById<TextView>(R.id.achievementDesc)
            val status = view.findViewById<TextView>(R.id.achievementStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_achievement, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (achievement, unlocked) = items[position]
            holder.icon.text   = icons.getOrElse(position) { "★" }
            holder.title.text  = achievement.title
            holder.desc.text   = achievement.description
            holder.status.text = if (unlocked) "✓" else "○"

            val accentColor = getColor(R.color.accent_cyan)
            val mutedColor  = getColor(R.color.text_muted)

            holder.icon.setTextColor(if (unlocked) accentColor else mutedColor)
            holder.title.setTextColor(if (unlocked) getColor(R.color.text_primary) else mutedColor)
            holder.status.setTextColor(if (unlocked) accentColor else mutedColor)
            holder.itemView.alpha = if (unlocked) 1f else 0.6f
        }

        override fun getItemCount() = items.size
    }
}
