package com.example.melow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RowState(val steps: BooleanArray, val count: Int, val soundResName: String, val pan: Float = 0f)

data class ProjectInfo(
    val fileName: String,
    val name: String,
    val bpm: Int,
    val createdAt: Long
) {
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
        return sdf.format(Date(createdAt))
    }
}

object ProjectManager {

    private const val DIR = "projects"

    private fun projectsDir(context: Context): File {
        return File(context.filesDir, DIR).also { it.mkdirs() }
    }

    fun saveProject(
        context: Context,
        name: String,
        bpm: Int,
        rows: Map<String, RowState>
    ): Boolean {
        return try {
            val createdAt = System.currentTimeMillis()
            val root = JSONObject().apply {
                put("name", name)
                put("bpm", bpm)
                put("createdAt", createdAt)
                val rowsJson = JSONObject()
                rows.forEach { (key, state) ->
                    val stepsArray = JSONArray()
                    state.steps.take(state.count).forEach { stepsArray.put(it) }
                    rowsJson.put(key, JSONObject().apply {
                        put("steps", stepsArray)
                        put("count", state.count)
                        put("soundResName", state.soundResName)
                        put("pan", state.pan.toDouble())
                    })
                }
                put("rows", rowsJson)
            }
            val file = File(projectsDir(context), "project_$createdAt.json")
            file.writeText(root.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listProjects(context: Context): List<ProjectInfo> {
        val dir = projectsDir(context)
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val json = JSONObject(file.readText())
                    ProjectInfo(
                        fileName = file.name,
                        name = json.getString("name"),
                        bpm = json.getInt("bpm"),
                        createdAt = json.getLong("createdAt")
                    )
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun loadProjectJson(context: Context, fileName: String): String? {
        return try {
            File(projectsDir(context), fileName).readText()
        } catch (e: Exception) {
            null
        }
    }

    fun deleteProject(context: Context, fileName: String): Boolean {
        return File(projectsDir(context), fileName).delete()
    }

    fun parseRows(json: String): Triple<Int, Map<String, RowState>, String> {
        val root = JSONObject(json)
        val bpm = root.getInt("bpm")
        val name = root.getString("name")
        val rowsJson = root.getJSONObject("rows")
        val rows = mutableMapOf<String, RowState>()
        rowsJson.keys().forEach { key ->
            val r = rowsJson.getJSONObject(key)
            val count = r.getInt("count")
            val stepsJson = r.getJSONArray("steps")
            val steps = BooleanArray(16)
            for (i in 0 until stepsJson.length()) {
                steps[i] = stepsJson.getBoolean(i)
            }
            rows[key] = RowState(steps, count, r.getString("soundResName"), r.optDouble("pan", 0.0).toFloat())
        }
        return Triple(bpm, rows, name)
    }
}
