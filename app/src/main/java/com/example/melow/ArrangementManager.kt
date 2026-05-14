package com.example.melow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BarEntry(val patternFileName: String, val patternName: String)

data class ArrangementSave(
    val name: String,
    val bpm: Int,
    val swing: Float,
    val bars: List<BarEntry>
)

object ArrangementManager {

    private const val FILE = "arrangement.json"

    fun save(context: Context, data: ArrangementSave): Boolean = try {
        val arr = JSONArray()
        data.bars.forEach { bar ->
            arr.put(JSONObject().apply {
                put("patternFileName", bar.patternFileName)
                put("patternName",     bar.patternName)
            })
        }
        val root = JSONObject().apply {
            put("name",  data.name)
            put("bpm",   data.bpm)
            put("swing", data.swing.toDouble())
            put("bars",  arr)
        }
        File(context.filesDir, FILE).writeText(root.toString())
        true
    } catch (_: Exception) { false }

    fun load(context: Context): ArrangementSave? = try {
        val root = JSONObject(File(context.filesDir, FILE).readText())
        val arr  = root.getJSONArray("bars")
        ArrangementSave(
            name  = root.optString("name", "Arrangement"),
            bpm   = root.optInt("bpm", 120),
            swing = root.optDouble("swing", 0.0).toFloat(),
            bars  = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BarEntry(obj.getString("patternFileName"), obj.getString("patternName"))
            }
        )
    } catch (_: Exception) { null }
}
