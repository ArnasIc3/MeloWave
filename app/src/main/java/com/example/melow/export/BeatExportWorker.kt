package com.example.melow.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.melow.data.RowState
import com.example.melow.data.SoundSettings
import org.json.JSONObject
import java.io.File

class BeatExportWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    companion object {
        const val KEY_PARAMS_FILE  = "params_file"
        const val KEY_OUTPUT_FILE  = "output_file"
        private const val CHANNEL_ID = "beat_export"
        private const val NOTIF_ID   = 1001
    }

    override fun doWork(): Result {
        val paramsPath = inputData.getString(KEY_PARAMS_FILE) ?: return Result.failure()
        val paramsFile = File(paramsPath)

        createChannel()
        notify("Exporting beat…", progress = 0, ongoing = true)

        return try {
            val json   = JSONObject(paramsFile.readText())
            paramsFile.delete()

            val userId = json.getLong("userId")
            val bpm    = json.getInt("bpm")
            val swing  = json.getDouble("swing").toFloat()

            val barsJson = json.getJSONArray("bars")
            val barRows  = (0 until barsJson.length()).map { i ->
                val rowObj = barsJson.getJSONObject(i)
                rowObj.keys().asSequence().associate { key ->
                    val r     = rowObj.getJSONObject(key)
                    val count = r.getInt("count")
                    val arr   = r.getJSONArray("steps")
                    val steps = BooleanArray(16).also { s -> for (j in 0 until arr.length()) s[j] = arr.getBoolean(j) }
                    key to RowState(steps, count, r.getString("soundResName"), r.optDouble("pan", 0.0).toFloat())
                }
            }

            fun floatMap(key: String) = json.getJSONObject(key).let { o ->
                o.keys().asSequence().associate { it to o.getDouble(it).toFloat() }
            }
            fun boolMap(key: String) = json.getJSONObject(key).let { o ->
                o.keys().asSequence().associate { it to o.getBoolean(it) }
            }
            val settings = json.getJSONObject("soundSettings").let { o ->
                o.keys().asSequence().associate { key ->
                    val s = o.getJSONObject(key)
                    key to SoundSettings(
                        displayName = s.optString("displayName", ""),
                        pitch       = s.optDouble("pitch", 1.0).toFloat(),
                        level       = s.optDouble("level", 1.0).toFloat()
                    )
                }
            }

            val file = BeatExporter.exportSync(
                context       = ctx,
                userId        = userId,
                bpm           = bpm,
                swing         = swing,
                barRows       = barRows,
                volumes       = floatMap("volumes"),
                pans          = floatMap("pans"),
                muted         = boolMap("muted"),
                soundSettings = settings,
                onProgress    = { pct -> notify("Exporting beat… $pct%", pct, ongoing = true) }
            )

            if (file != null) {
                notifyDone(file)
                Result.success(workDataOf(KEY_OUTPUT_FILE to file.absolutePath))
            } else {
                notify("Export failed", progress = -1, ongoing = false)
                Result.failure()
            }
        } catch (e: Exception) {
            paramsFile.delete()
            notify("Export failed", progress = -1, ongoing = false)
            Result.failure()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Beat Export", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun canNotify() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(title: String, progress: Int, ongoing: Boolean) {
        if (!canNotify()) return
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setOngoing(ongoing)
        if (progress in 0..99) builder.setProgress(100, progress, false)
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, builder.build())
    }

    private fun notifyDone(file: File) {
        if (!canNotify()) return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            ctx, 0,
            Intent.createChooser(shareIntent, "Share beat via…").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Export complete!")
                .setContentText("Tap to share your beat")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }
}
