package com.example.melow.data

import android.content.Context
import android.widget.Toast

data class Achievement(
    val id: String,
    val title: String,
    val description: String
)

object AchievementManager {

    private const val PREFS = "achievements"

    val all = listOf(
        Achievement("first_beat",    "First Beat",      "Save your first beat project"),
        Achievement("exporter",      "Exporter",        "Export a beat as WAV"),
        Achievement("arranger",      "Arranger",        "Add a pattern to the Arrangement"),
        Achievement("custom_sound",  "Sound Collector", "Import or record a custom sound"),
        Achievement("full_kit",      "Full Kit",        "Activate steps on all 5 instruments in one beat"),
        Achievement("speed_demon",   "Speed Demon",     "Set BPM to 160 or higher"),
        Achievement("swing_master",  "Swing Master",    "Use swing above 30%")
    )

    fun unlock(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(id, false)) return
        prefs.edit().putBoolean(id, true).apply()
        val title = all.firstOrNull { it.id == id }?.title ?: return
        Toast.makeText(context, "Achievement unlocked: $title", Toast.LENGTH_SHORT).show()
    }

    fun isUnlocked(context: Context, id: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(id, false)

    fun unlockedCount(context: Context): Int =
        all.count { isUnlocked(context, it.id) }
}
