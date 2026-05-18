package com.example.melow

import android.content.Context
import org.json.JSONObject
import java.io.File

data class SoundSettings(
    val displayName: String = "",
    val pitch: Float = 1.0f,   // 0.5 (lower) – 2.0 (higher), SoundPool rate param
    val level: Float = 1.0f    // 0.0 – 1.0 per-sound gain
)

object SoundSettingsManager {

    private fun file(context: Context, userId: Long) =
        File(context.filesDir, "sound_settings_$userId.json")

    fun getAll(context: Context, userId: Long): MutableMap<String, SoundSettings> {
        return try {
            val json = JSONObject(file(context, userId).readText())
            val map = mutableMapOf<String, SoundSettings>()
            json.keys().forEach { key ->
                val o = json.getJSONObject(key)
                map[key] = SoundSettings(
                    displayName = o.optString("displayName", ""),
                    pitch       = o.optDouble("pitch", 1.0).toFloat(),
                    level       = o.optDouble("level", 1.0).toFloat()
                )
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun update(context: Context, userId: Long, resName: String, settings: SoundSettings) {
        val all = getAll(context, userId)
        all[resName] = settings
        val json = JSONObject()
        all.forEach { (k, s) ->
            json.put(k, JSONObject().apply {
                put("displayName", s.displayName)
                put("pitch", s.pitch.toDouble())
                put("level", s.level.toDouble())
            })
        }
        file(context, userId).writeText(json.toString())
    }
}
