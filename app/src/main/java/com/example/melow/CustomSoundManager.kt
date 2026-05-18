package com.example.melow

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object CustomSoundManager {

    private val SUPPORTED = setOf("wav", "mp3", "ogg", "m4a", "aac")

    fun customSoundsDir(context: Context, userId: Long): File =
        File(context.filesDir, "custom_sounds/user_$userId").also { it.mkdirs() }

    fun importSound(context: Context, userId: Long, uri: Uri): String? {
        return try {
            val rawName = resolveFileName(context, uri)
                ?: "sound_${System.currentTimeMillis()}.wav"
            val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val ext = safeName.substringAfterLast('.', "wav").lowercase()
            if (ext !in SUPPORTED) return null

            val dest = File(customSoundsDir(context, userId), safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            safeName
        } catch (e: Exception) {
            null
        }
    }

    fun listSounds(context: Context, userId: Long): List<SoundItem> =
        customSoundsDir(context, userId)
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

    fun deleteSound(context: Context, userId: Long, fileName: String): Boolean =
        File(customSoundsDir(context, userId), fileName).delete()

    fun fileForResName(context: Context, userId: Long, resName: String): File? {
        if (!resName.startsWith("custom:")) return null
        val fileName = resName.removePrefix("custom:")
        val file = File(customSoundsDir(context, userId), fileName)
        return if (file.exists()) file else null
    }

    fun saveRecording(context: Context, userId: Long, pcmData: ByteArray, sampleRate: Int): String? {
        return try {
            val fileName = "rec_${System.currentTimeMillis()}.wav"
            val file = File(customSoundsDir(context, userId), fileName)
            file.outputStream().use { out ->
                val numChannels = 1
                val bitsPerSample = 16
                val byteRate = sampleRate * numChannels * bitsPerSample / 8
                val blockAlign = numChannels * bitsPerSample / 8
                val dataSize = pcmData.size
                val totalSize = 36 + dataSize
                fun Int.le4() = byteArrayOf(toByte(), shr(8).toByte(), shr(16).toByte(), shr(24).toByte())
                fun Int.le2() = byteArrayOf(toByte(), shr(8).toByte())
                out.write("RIFF".toByteArray())
                out.write(totalSize.le4())
                out.write("WAVE".toByteArray())
                out.write("fmt ".toByteArray())
                out.write(16.le4())
                out.write(1.le2())
                out.write(numChannels.le2())
                out.write(sampleRate.le4())
                out.write(byteRate.le4())
                out.write(blockAlign.le2())
                out.write(bitsPerSample.le2())
                out.write("data".toByteArray())
                out.write(dataSize.le4())
                out.write(pcmData)
            }
            fileName
        } catch (e: Exception) {
            null
        }
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
