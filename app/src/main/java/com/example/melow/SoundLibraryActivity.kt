package com.example.melow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream

class SoundLibraryActivity : AppCompatActivity() {

    private lateinit var soundPool: SoundPool
    private val loadedSounds = mutableMapOf<String, Int>()

    private val builtInSounds = listOf(
        SoundItem("Kick Drum",  "Drums",      "kick",    R.raw.kick),
        SoundItem("Snare Drum", "Drums",      "snare",   R.raw.snare),
        SoundItem("Hi-Hat",     "Cymbals",    "hihat",   R.raw.hihat),
        SoundItem("Open Hat",   "Cymbals",    "openhat", R.raw.openhat),
        SoundItem("Clap",       "Percussion", "clap",    R.raw.clap)
    )

    private val allSounds = mutableListOf<SoundItem>()
    private lateinit var adapter: SoundAdapter
    private var currentResName = ""
    private var settingsCache = mutableMapOf<String, SoundSettings>()

    private var userId = -1L

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingDialog()
        else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val fileName = CustomSoundManager.importSound(this, userId, uri)
        if (fileName != null) {
            val newItem = SoundItem(
                name     = fileName.substringBeforeLast('.'),
                category = "Custom",
                resName  = "custom:$fileName",
                resId    = 0,
                filePath = CustomSoundManager.customSoundsDir(this, userId).resolve(fileName).absolutePath
            )
            loadCustomIntoPool(newItem)
            allSounds.add(newItem)
            adapter.notifyItemInserted(allSounds.size - 1)
            Toast.makeText(this, "\"${newItem.name}\" added", Toast.LENGTH_SHORT).show()
            AchievementManager.unlock(this, "custom_sound")
        } else {
            Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_library)

        userId = intent.getLongExtra("userId", -1L)
        currentResName = intent.getStringExtra("currentResName") ?: ""
        val rowName = intent.getStringExtra("rowName") ?: ""
        findViewById<TextView>(R.id.editingRowLabel).text =
            if (rowName.isNotEmpty()) "Editing: $rowName" else ""

        settingsCache = SoundSettingsManager.getAll(this, userId)
        setupSoundPool()
        buildSoundList()

        adapter = SoundAdapter(
            sounds         = allSounds,
            currentResName = currentResName,
            onPreview      = { sound -> previewSound(sound) },
            onSelect       = { sound -> returnSound(sound) },
            onEdit         = { sound -> showEditDialog(sound) },
            onDelete       = { sound, pos -> confirmDelete(sound, pos) }
        )

        findViewById<RecyclerView>(R.id.soundsRecycler).apply {
            layoutManager = LinearLayoutManager(this@SoundLibraryActivity)
            this.adapter = this@SoundLibraryActivity.adapter
        }

        findViewById<Button>(R.id.addSoundButton).setOnClickListener {
            filePicker.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.recordSoundButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) startRecordingDialog()
            else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        findViewById<Button>(R.id.backButton).setOnClickListener { navigateBack() }
    }

    private fun setupSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        builtInSounds.forEach { loadedSounds[it.resName] = soundPool.load(this, it.resId, 1) }
    }

    private fun buildSoundList() {
        allSounds.clear()
        // Apply saved display names to built-in sounds
        builtInSounds.forEach { sound ->
            val saved = settingsCache[sound.resName]
            allSounds.add(if (!saved?.displayName.isNullOrEmpty()) sound.copy(name = saved!!.displayName) else sound)
        }
        val custom = CustomSoundManager.listSounds(this, userId)
        custom.forEach { sound ->
            loadCustomIntoPool(sound)
            val saved = settingsCache[sound.resName]
            allSounds.add(if (!saved?.displayName.isNullOrEmpty()) sound.copy(name = saved!!.displayName) else sound)
        }
    }

    private fun loadCustomIntoPool(sound: SoundItem) {
        sound.filePath ?: return
        loadedSounds[sound.resName] = soundPool.load(sound.filePath, 1)
    }

    private fun previewSound(sound: SoundItem) {
        val settings = settingsCache[sound.resName] ?: SoundSettings()
        loadedSounds[sound.resName]?.let { id ->
            val vol = (settings.level * 0.85f).coerceAtMost(0.85f)
            soundPool.play(id, vol, vol, 1, 0, settings.pitch)
        }
    }

    private fun returnSound(sound: SoundItem) {
        val result = Intent().apply {
            putExtra("soundResName", sound.resName)
            putExtra("soundDisplayName", sound.name)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun showEditDialog(sound: SoundItem) {
        val saved = settingsCache[sound.resName] ?: SoundSettings()
        var currentPitch = saved.pitch
        var currentLevel = saved.level

        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (16 * dp).toInt(), pad, 0)
            setBackgroundColor(getColor(R.color.bg_surface))
        }

        // Name field
        val nameEdit = EditText(this).apply {
            val currentName = if (saved.displayName.isNotEmpty()) saved.displayName else sound.name
            setText(currentName)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            hint = "Sound name"
            isEnabled = true
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.accent_purple))
        }
        layout.addView(nameEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (16 * dp).toInt() })

        // Pitch
        val pitchLabel = TextView(this).apply {
            text = "Pitch  ${String.format("%.2f", currentPitch)}x"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
        }
        layout.addView(pitchLabel)

        val pitchBar = SeekBar(this).apply {
            max = 30
            progress = ((currentPitch - 0.5f) / 1.5f * 30).toInt().coerceIn(0, 30)
            progressTintList = ColorStateList.valueOf(getColor(R.color.accent_purple))
            thumbTintList = ColorStateList.valueOf(getColor(R.color.accent_purple))
            progressBackgroundTintList = ColorStateList.valueOf(getColor(R.color.step_inactive))
        }
        layout.addView(pitchBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (16 * dp).toInt() })

        // Level
        val levelLabel = TextView(this).apply {
            text = "Level  ${(currentLevel * 100).toInt()}%"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
        }
        layout.addView(levelLabel)

        val levelBar = SeekBar(this).apply {
            max = 100
            progress = (currentLevel * 100).toInt()
            progressTintList = ColorStateList.valueOf(getColor(R.color.accent_cyan))
            thumbTintList = ColorStateList.valueOf(getColor(R.color.accent_cyan))
            progressBackgroundTintList = ColorStateList.valueOf(getColor(R.color.step_inactive))
        }
        layout.addView(levelBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (12 * dp).toInt() })

        // Preview button
        val previewBtn = Button(this).apply {
            text = "▶  Preview"
            setBackgroundResource(R.drawable.bg_btn_secondary)
            setTextColor(getColor(R.color.text_secondary))
            stateListAnimator = null
        }
        layout.addView(previewBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()
        ).also { it.bottomMargin = (8 * dp).toInt() })

        // Listener wiring
        pitchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                currentPitch = 0.5f + (p / 30f) * 1.5f
                pitchLabel.text = "Pitch  ${String.format("%.2f", currentPitch)}x"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        levelBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                currentLevel = p / 100f
                levelLabel.text = "Level  $p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        previewBtn.setOnClickListener {
            loadedSounds[sound.resName]?.let { id ->
                val vol = (currentLevel * 0.85f).coerceAtMost(0.85f)
                soundPool.play(id, vol, vol, 1, 0, currentPitch)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Edit: ${sound.name}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameEdit.text.toString().trim().ifEmpty { sound.name }
                val newSettings = SoundSettings(displayName = newName, pitch = currentPitch, level = currentLevel)
                SoundSettingsManager.update(this, userId, sound.resName, newSettings)
                settingsCache[sound.resName] = newSettings
                val idx = allSounds.indexOfFirst { it.resName == sound.resName }
                if (idx >= 0) adapter.updateAt(idx, sound.copy(name = newName))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(sound: SoundItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${sound.name}\"?")
            .setMessage("This will remove the custom sound file.")
            .setPositiveButton("Delete") { _, _ ->
                val fileName = sound.resName.removePrefix("custom:")
                CustomSoundManager.deleteSound(this, userId, fileName)
                adapter.removeAt(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startRecordingDialog() {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).also { if (it.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException() }
        } catch (e: Exception) {
            Toast.makeText(this, "Microphone not available", Toast.LENGTH_SHORT).show()
            return
        }
        audioRecord = record

        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
            setBackgroundColor(getColor(R.color.bg_surface))
        }

        val indicator = TextView(this).apply {
            text = "● REC  0:00"
            textSize = 22f
            setTextColor(getColor(R.color.button_danger))
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(indicator, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (8 * dp).toInt() })

        val hint = TextView(this).apply {
            text = "Tap Stop to finish"
            textSize = 13f
            setTextColor(getColor(R.color.text_muted))
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(hint)

        var elapsedSeconds = 0
        val timerRunnable = object : Runnable {
            override fun run() {
                if (!isRecording) return
                elapsedSeconds++
                val m = elapsedSeconds / 60
                val s = elapsedSeconds % 60
                indicator.text = "● REC  $m:${s.toString().padStart(2, '0')}"
                uiHandler.postDelayed(this, 1000)
            }
        }

        val pcmOut = ByteArrayOutputStream()
        isRecording = true
        record.startRecording()
        uiHandler.postDelayed(timerRunnable, 1000)

        Thread {
            val buf = ShortArray(bufferSize / 2)
            while (isRecording) {
                val read = record.read(buf, 0, buf.size)
                if (read > 0) {
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        bytes[i * 2]     = (buf[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (buf[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    synchronized(pcmOut) { pcmOut.write(bytes) }
                }
            }
            record.stop()
            record.release()
            audioRecord = null

            val pcm = synchronized(pcmOut) { pcmOut.toByteArray() }
            val fileName = CustomSoundManager.saveRecording(this, userId, pcm, sampleRate)
            uiHandler.post {
                if (fileName != null) {
                    val newItem = SoundItem(
                        name     = fileName.substringBeforeLast('.'),
                        category = "Custom",
                        resName  = "custom:$fileName",
                        resId    = 0,
                        filePath = CustomSoundManager.customSoundsDir(this, userId).resolve(fileName).absolutePath
                    )
                    loadCustomIntoPool(newItem)
                    allSounds.add(newItem)
                    adapter.notifyItemInserted(allSounds.size - 1)
                    Toast.makeText(this, "\"${newItem.name}\" saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save recording", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()

        AlertDialog.Builder(this)
            .setTitle("Recording")
            .setView(layout)
            .setPositiveButton("Stop") { _, _ ->
                isRecording = false
                uiHandler.removeCallbacks(timerRunnable)
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateBack() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        soundPool.release()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
