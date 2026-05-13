package com.example.melow

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object CustomSoundManager {

    private const val DIR = "custom_sounds"
    private val SUPPORTED = setOf("wav", "mp3", "ogg", "m4a", "aac")

    fun customSoundsDir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    fun importSound(context: Context, uri: Uri): String? {
        return try {
            val rawName = resolveFileName(context, uri)
                ?: "sound_${System.currentTimeMillis()}.wav"
            val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val ext = safeName.substringAfterLast('.', "wav").lowercase()
            if (ext !in SUPPORTED) return null

            val dest = File(customSoundsDir(context), safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            safeName
        } catch (e: Exception) {
            null
        }
    }

    fun listSounds(context: Context): List<SoundItem> =
        customSoundsDir(context)
            .listFiles { f -> f.extension.lowercase() in SUPPORTED }
            ?.sortedBy { it.name }
            ?.map { file ->
                SoundItem(
                    name = file.nameWithoutExtension,
                    category = "Custom",
                    resName = "custom:${file.name}",
                    resId = 0,
                    filePath = file.absolutePath
                )
            } ?: emptyList()

    fun deleteSound(context: Context, fileName: String): Boolean =
        File(customSoundsDir(context), fileName).delete()

    fun fileForResName(context: Context, resName: String): File? {
        if (!resName.startsWith("custom:")) return null
        val fileName = resName.removePrefix("custom:")
        val file = File(customSoundsDir(context), fileName)
        return if (file.exists()) file else null
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
