package com.example.melow.data

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

data class ArrangementMeta(
    val fileName: String,
    val name: String,
    val bpm: Int,
    val barCount: Int,
    val lastModified: Long
)

object ArrangementManager {

    private fun dir(context: Context, userId: Long): File =
        File(context.filesDir, "arrangements_$userId").also { it.mkdirs() }

    fun list(context: Context, userId: Long): List<ArrangementMeta> =
        dir(context, userId).listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f ->
                try {
                    val root = JSONObject(f.readText())
                    ArrangementMeta(
                        fileName     = f.name,
                        name         = root.optString("name", "Arrangement"),
                        bpm          = root.optInt("bpm", 120),
                        barCount     = root.optJSONArray("bars")?.length() ?: 0,
                        lastModified = f.lastModified()
                    )
                } catch (_: Exception) { null }
            } ?: emptyList()

    fun save(context: Context, userId: Long, data: ArrangementSave, fileName: String? = null): String? = try {
        val fn = fileName ?: "arr_${System.currentTimeMillis()}.json"
        File(dir(context, userId), fn).writeText(buildJson(data).toString())
        fn
    } catch (_: Exception) { null }

    fun load(context: Context, userId: Long, fileName: String): ArrangementSave? = try {
        parseJson(File(dir(context, userId), fileName).readText())
    } catch (_: Exception) { null }

    private fun buildJson(data: ArrangementSave): JSONObject {
        val arr = JSONArray()
        data.bars.forEach { b ->
            arr.put(JSONObject().apply {
                put("patternFileName", b.patternFileName)
                put("patternName",     b.patternName)
            })
        }
        return JSONObject().apply {
            put("name",  data.name)
            put("bpm",   data.bpm)
            put("swing", data.swing.toDouble())
            put("bars",  arr)
        }
    }

    private fun parseJson(text: String): ArrangementSave {
        val root = JSONObject(text)
        val arr  = root.getJSONArray("bars")
        return ArrangementSave(
            name  = root.optString("name", "Arrangement"),
            bpm   = root.optInt("bpm", 120),
            swing = root.optDouble("swing", 0.0).toFloat(),
            bars  = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                BarEntry(o.getString("patternFileName"), o.getString("patternName"))
            }
        )
    }
}
