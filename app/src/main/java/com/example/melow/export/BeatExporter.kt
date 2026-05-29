package com.example.melow.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.melow.R
import com.example.melow.data.CustomSoundManager
import com.example.melow.data.RowState
import com.example.melow.data.SoundSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

object BeatExporter {

    private const val SAMPLE_RATE = 44100

    private val builtInResIds = mapOf(
        "kick"    to R.raw.kick,
        "snare"   to R.raw.snare,
        "hihat"   to R.raw.hihat,
        "openhat" to R.raw.openhat,
        "clap"    to R.raw.clap
    )

    // ── Public async API (used by activities directly) ────────────────────────

    fun export(
        context: Context,
        userId: Long,
        bpm: Int,
        rows: Map<String, RowState>,
        volumes: Map<String, Float>,
        pans: Map<String, Float>,
        muted: Map<String, Boolean>,
        soundSettings: Map<String, SoundSettings>,
        swing: Float = 0f,
        repeatBars: Int = 4,
        onProgress: (Int) -> Unit,
        onDone: (File?) -> Unit
    ) {
        Thread {
            onDone(mix(context, userId, bpm, swing, List(repeatBars) { rows },
                volumes, pans, muted, soundSettings, onProgress))
        }.start()
    }

    fun exportArrangement(
        context: Context,
        userId: Long,
        bpm: Int,
        swing: Float,
        barRows: List<Map<String, RowState>>,
        volumes: Map<String, Float>,
        pans: Map<String, Float>,
        muted: Map<String, Boolean>,
        soundSettings: Map<String, SoundSettings>,
        onProgress: (Int) -> Unit,
        onDone: (File?) -> Unit
    ) {
        Thread {
            onDone(mix(context, userId, bpm, swing, barRows,
                volumes, pans, muted, soundSettings, onProgress))
        }.start()
    }

    // ── Public sync API (called by BeatExportWorker on its background thread) ─

    fun exportSync(
        context: Context,
        userId: Long,
        bpm: Int,
        swing: Float,
        barRows: List<Map<String, RowState>>,
        volumes: Map<String, Float>,
        pans: Map<String, Float>,
        muted: Map<String, Boolean>,
        soundSettings: Map<String, SoundSettings>,
        onProgress: (Int) -> Unit = {}
    ): File? = mix(context, userId, bpm, swing, barRows, volumes, pans, muted, soundSettings, onProgress)

    // ── Params serialisation (write before enqueuing WorkManager job) ─────────

    fun writeParamsFile(
        context: Context,
        userId: Long,
        bpm: Int,
        swing: Float,
        barRows: List<Map<String, RowState>>,
        volumes: Map<String, Float>,
        pans: Map<String, Float>,
        muted: Map<String, Boolean>,
        soundSettings: Map<String, SoundSettings>
    ): File {
        val barsJson = JSONArray()
        barRows.forEach { rows ->
            val rowsObj = JSONObject()
            rows.forEach { (key, state) ->
                val steps = JSONArray().also { arr -> state.steps.take(state.count).forEach { arr.put(it) } }
                rowsObj.put(key, JSONObject().apply {
                    put("steps", steps)
                    put("count", state.count)
                    put("soundResName", state.soundResName)
                    put("pan", state.pan.toDouble())
                })
            }
            barsJson.put(rowsObj)
        }

        fun floatMap(m: Map<String, Float>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v.toDouble()) } }
        fun boolMap(m: Map<String, Boolean>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }
        fun settingsMap(m: Map<String, SoundSettings>) = JSONObject().also { o ->
            m.forEach { (k, s) -> o.put(k, JSONObject().apply {
                put("displayName", s.displayName)
                put("pitch", s.pitch.toDouble())
                put("level", s.level.toDouble())
            }) }
        }

        val json = JSONObject().apply {
            put("userId", userId)
            put("bpm", bpm)
            put("swing", swing.toDouble())
            put("bars", barsJson)
            put("volumes", floatMap(volumes))
            put("pans", floatMap(pans))
            put("muted", boolMap(muted))
            put("soundSettings", settingsMap(soundSettings))
        }

        val file = File(context.cacheDir, "export_params_${System.currentTimeMillis()}.json")
        file.writeText(json.toString())
        return file
    }

    // ── Core mixer (runs on whatever thread calls it) ─────────────────────────

    private fun mix(
        context: Context,
        userId: Long,
        bpm: Int,
        swing: Float,
        barRows: List<Map<String, RowState>>,
        volumes: Map<String, Float>,
        pans: Map<String, Float>,
        muted: Map<String, Boolean>,
        soundSettings: Map<String, SoundSettings>,
        onProgress: (Int) -> Unit
    ): File? {
        return try {
            val stepMs         = 60_000.0 / bpm / 4
            val samplesPerStep = (stepMs * SAMPLE_RATE / 1000).toInt()
            val totalSamples   = samplesPerStep * 16 * barRows.size
            val mix            = FloatArray(totalSamples * 2)

            val uniqueResNames = barRows.flatMap { it.values.map { r -> r.soundResName } }.toSet()
            val decoded = mutableMapOf<String, ShortArray>()
            uniqueResNames.forEachIndexed { i, resName ->
                decoded[resName] = decodeToMono(context, userId, resName) ?: ShortArray(0)
                onProgress((i + 1) * 25 / uniqueResNames.size.coerceAtLeast(1))
            }

            barRows.forEachIndexed { barIdx, rows ->
                for (s in 0 until 16) {
                    val globalStep   = barIdx * 16 + s
                    val sampleOffset = stepStartSamples(globalStep, samplesPerStep, swing)

                    rows.forEach { (key, rowState) ->
                        if (muted[key] == true) return@forEach
                        val count = rowState.count
                        if (count == 8 && s % 2 != 0) return@forEach
                        val resolvedIdx = if (count == 8) s / 2 else s
                        if (!rowState.steps[resolvedIdx]) return@forEach

                        val pcm      = decoded[rowState.soundResName]?.takeIf { it.isNotEmpty() } ?: return@forEach
                        val settings = soundSettings[rowState.soundResName] ?: SoundSettings()
                        val vol      = ((volumes[key] ?: 1f) * settings.level).coerceIn(0f, 1f)
                        val pan      = (pans[key] ?: 0f).coerceIn(-1f, 1f)
                        val leftGain  = vol * if (pan >= 0f) 1f else 1f + pan
                        val rightGain = vol * if (pan <= 0f) 1f else 1f - pan
                        val pitched  = applyPitch(pcm, settings.pitch)

                        for (i in pitched.indices) {
                            val dst = sampleOffset + i
                            if (dst >= totalSamples) break
                            val sample = pitched[i].toFloat() / Short.MAX_VALUE
                            mix[dst * 2]     += sample * leftGain
                            mix[dst * 2 + 1] += sample * rightGain
                        }
                    }
                }
                onProgress(25 + (barIdx + 1) * 65 / barRows.size.coerceAtLeast(1))
            }

            val peak  = mix.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1f) ?: 1f
            val scale = if (peak > 0.99f) 0.99f / peak else 1f
            val pcmOut = ShortArray(mix.size) { i ->
                (mix[i] * scale * Short.MAX_VALUE)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val outFile = File(File(context.cacheDir, "exports").also { it.mkdirs() },
                "melowave_${System.currentTimeMillis()}.wav")
            writeWav(outFile, pcmOut, SAMPLE_RATE, 2)
            onProgress(100)
            outFile
        } catch (_: Exception) {
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stepStartSamples(step: Int, samplesPerStep: Int, swing: Float): Int =
        if (step % 2 == 0) step * samplesPerStep
        else (step - 1) * samplesPerStep + (samplesPerStep * (1f + swing)).toInt()

    private fun decodeToMono(context: Context, userId: Long, resName: String): ShortArray? {
        val extractor = MediaExtractor()
        return try {
            if (resName.startsWith("custom:")) {
                val file = CustomSoundManager.fileForResName(context, userId, resName) ?: return null
                extractor.setDataSource(file.absolutePath)
            } else {
                val resId = builtInResIds[resName] ?: return null
                extractor.setDataSource(context,
                    Uri.parse("android.resource://${context.packageName}/$resId"), null)
            }

            val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null

            extractor.selectTrack(trackIdx)
            val format      = extractor.getTrackFormat(trackIdx)
            val mime        = format.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate     = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val rawPcm = ByteArrayOutputStream()
            val info   = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false

            while (!outDone) {
                if (!inDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf  = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val buf   = codec.getOutputBuffer(outIdx)!!
                    val bytes = ByteArray(info.size)
                    buf.get(bytes)
                    rawPcm.write(bytes)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            codec.stop(); codec.release(); extractor.release()

            val bytes  = rawPcm.toByteArray()
            val shorts = ShortArray(bytes.size / 2) { i ->
                ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
            val mono = if (srcChannels == 2)
                ShortArray(shorts.size / 2) { i ->
                    ((shorts[i * 2].toInt() + shorts[i * 2 + 1].toInt()) / 2).toShort()
                }
            else shorts
            if (srcRate != SAMPLE_RATE) resample(mono, srcRate, SAMPLE_RATE) else mono
        } catch (_: Exception) {
            extractor.release(); null
        }
    }

    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        val ratio      = fromRate.toDouble() / toRate
        val outputSize = (input.size / ratio).toInt()
        return ShortArray(outputSize) { i ->
            val srcIdx = i * ratio
            val lo = srcIdx.toInt().coerceIn(0, input.size - 1)
            val hi = (lo + 1).coerceIn(0, input.size - 1)
            (input[lo] * (1.0 - (srcIdx - lo)) + input[hi] * (srcIdx - lo)).toInt().toShort()
        }
    }

    private fun applyPitch(pcm: ShortArray, pitch: Float): ShortArray {
        if (pitch == 1f) return pcm
        val ratio      = 1.0 / pitch
        val outputSize = (pcm.size * ratio).toInt()
        return ShortArray(outputSize) { i ->
            val srcIdx = i / ratio
            val lo = srcIdx.toInt().coerceIn(0, pcm.size - 1)
            val hi = (lo + 1).coerceIn(0, pcm.size - 1)
            (pcm[lo] * (1.0 - (srcIdx - lo)) + pcm[hi] * (srcIdx - lo)).toInt().toShort()
        }
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int, channels: Int) {
        file.outputStream().use { out ->
            val bitsPerSample = 16
            val byteRate   = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize   = pcm.size * 2
            fun Int.le4() = byteArrayOf(toByte(), shr(8).toByte(), shr(16).toByte(), shr(24).toByte())
            fun Int.le2() = byteArrayOf(toByte(), shr(8).toByte())
            out.write("RIFF".toByteArray()); out.write((36 + dataSize).le4())
            out.write("WAVE".toByteArray()); out.write("fmt ".toByteArray())
            out.write(16.le4()); out.write(1.le2()); out.write(channels.le2())
            out.write(sampleRate.le4()); out.write(byteRate.le4())
            out.write(blockAlign.le2()); out.write(bitsPerSample.le2())
            out.write("data".toByteArray()); out.write(dataSize.le4())
            val buf = ByteArray(2)
            pcm.forEach { s ->
                buf[0] = (s.toInt() and 0xFF).toByte()
                buf[1] = (s.toInt() shr 8 and 0xFF).toByte()
                out.write(buf)
            }
        }
    }
}
