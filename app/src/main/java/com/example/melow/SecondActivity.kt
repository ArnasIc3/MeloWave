package com.example.melow

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf

class SecondActivity : AppCompatActivity() {

    private val maxSteps = 16

    private var kickStepsCount = 8
    private var snareStepsCount = 8
    private var hatStepsCount = 8
    private var openHatStepsCount = 8
    private var clapStepsCount = 8

    private lateinit var soundPool: android.media.SoundPool

    private var kickSound = 0
    private var snareSound = 0
    private var hatSound = 0
    private var openHatSound = 0
    private var clapSound = 0

    private lateinit var kickRow: LinearLayout
    private lateinit var snareRow: LinearLayout
    private lateinit var hatRow: LinearLayout
    private lateinit var openHatRow: LinearLayout
    private lateinit var clapRow: LinearLayout

    private val kickSteps = BooleanArray(maxSteps)
    private val snareSteps = BooleanArray(maxSteps)
    private val hatSteps = BooleanArray(maxSteps)
    private val openHatSteps = BooleanArray(maxSteps)
    private val clapSteps = BooleanArray(maxSteps)

    private var kickSoundResName = "kick"
    private var snareSoundResName = "snare"
    private var hatSoundResName = "hihat"
    private var openHatSoundResName = "openhat"
    private var clapSoundResName = "clap"

    private var kickInstrument = 0
    private var snareInstrument = 0
    private var hatInstrument = 0
    private var openHatInstrument = 0
    private var clapInstrument = 0

    private val soundMap = mutableMapOf<String, Int>()

    // Real-time audio controls
    private val muted   = mutableMapOf("kick" to false, "snare" to false, "hat" to false, "openHat" to false, "clap" to false)
    private val volumes = mutableMapOf("kick" to 1f,    "snare" to 1f,    "hat" to 1f,    "openHat" to 1f,    "clap" to 1f)
    private val pans    = mutableMapOf("kick" to 0f,    "snare" to 0f,    "hat" to 0f,    "openHat" to 0f,    "clap" to 0f)

    // Per-sound pitch/level/name settings
    @Volatile private var soundSettingsCache = mapOf<String, SoundSettings>()

    // Global audio effects
    private var reverbEffect: PresetReverb? = null
    private var equalizerEffect: Equalizer? = null

    private val soundDisplayNames = mapOf(
        "kick"    to "Kick Drum",
        "snare"   to "Snare Drum",
        "hihat"   to "Hi-Hat",
        "openhat" to "Open Hat",
        "clap"    to "Clap"
    )

    private var userId = -1L
    private var hasUnsavedChanges = false

    private var loadingOverlay: View? = null
    @Volatile private var loadedSoundCount = 0

    private val sequencerThread = android.os.HandlerThread("SequencerThread").also { it.start() }
    private val sequencerHandler = Handler(sequencerThread.looper)
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile private var currentStep = 0
    @Volatile private var isPlaying = false

    private var bpm = 120
    private var stepDuration = calcStepDuration(120)
    private var swing = 0f   // 0.0 (straight) .. 0.5 (triplet)

    private var loadedProjectName: String? = null

    private var currentEditingRow = ""

    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resName = result.data?.getStringExtra("soundResName") ?: return@registerForActivityResult
            // If custom sound not yet in map (uploaded during this session), load it now
            if (!soundMap.containsKey(resName)) {
                CustomSoundManager.fileForResName(this, userId, resName)?.let { file ->
                    soundMap[resName] = soundPool.load(file.absolutePath, 1)
                }
            }
            val soundId = soundMap[resName] ?: return@registerForActivityResult
            when (currentEditingRow) {
                "kick"    -> { kickInstrument = soundId;    kickSoundResName = resName;    updateRowLabel(R.id.kickLabel,    resName) }
                "snare"   -> { snareInstrument = soundId;   snareSoundResName = resName;   updateRowLabel(R.id.snareLabel,   resName) }
                "hat"     -> { hatInstrument = soundId;     hatSoundResName = resName;     updateRowLabel(R.id.hatLabel,     resName) }
                "openHat" -> { openHatInstrument = soundId; openHatSoundResName = resName; updateRowLabel(R.id.openHatLabel, resName) }
                "clap"    -> { clapInstrument = soundId;    clapSoundResName = resName;    updateRowLabel(R.id.clapLabel,   resName) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        userId = intent.getLongExtra("userId", -1L)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })

        showLoadingOverlay()

        setupSoundPool()
        initAudioEffects()
        soundSettingsCache = SoundSettingsManager.getAll(this, userId)
        intent.getStringExtra("projectJson")?.let { loadProjectData(it) }
        loadedProjectName = intent.getStringExtra("projectName")
        setupUI()
    }

    private fun showLoadingOverlay() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        loadingOverlay = LayoutInflater.from(this).inflate(R.layout.view_loading_overlay, root, false)
        root.addView(loadingOverlay)
    }

    private fun dismissLoadingOverlay() {
        loadingOverlay?.animate()
            ?.alpha(0f)
            ?.setDuration(300)
            ?.withEndAction { loadingOverlay?.visibility = View.GONE }
            ?.start()
    }

    private fun setupSoundPool() {
        val audioAttrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, _ ->
            loadedSoundCount++
            if (loadedSoundCount >= 5) {
                uiHandler.post { dismissLoadingOverlay() }
            }
        }

        kickSound    = soundPool.load(this, R.raw.kick,    1)
        snareSound   = soundPool.load(this, R.raw.snare,   1)
        hatSound     = soundPool.load(this, R.raw.hihat,   1)
        openHatSound = soundPool.load(this, R.raw.openhat, 1)
        clapSound    = soundPool.load(this, R.raw.clap,    1)

        soundMap["kick"]    = kickSound
        soundMap["snare"]   = snareSound
        soundMap["hihat"]   = hatSound
        soundMap["openhat"] = openHatSound
        soundMap["clap"]    = clapSound

        kickInstrument    = kickSound
        snareInstrument   = snareSound
        hatInstrument     = hatSound
        openHatInstrument = openHatSound
        clapInstrument    = clapSound

        CustomSoundManager.listSounds(this, userId).forEach { sound ->
            sound.filePath?.let { path ->
                soundMap[sound.resName] = soundPool.load(path, 1)
            }
        }
    }

    private fun initAudioEffects() {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val fallbackSession = am.generateAudioSessionId()

        reverbEffect = tryCreateReverb(0) ?: tryCreateReverb(fallbackSession)
        equalizerEffect = tryCreateEq(0) ?: tryCreateEq(fallbackSession)

        val prefs = getSharedPreferences("audio_effects", MODE_PRIVATE)
        val reverbIdx = prefs.getInt("reverb_preset_idx", 0)
        if (reverbIdx > 0) applyReverbPreset(reverbIdx)
        restoreEqLevels(prefs.getString("eq_levels", null))
    }

    private fun tryCreateReverb(sessionId: Int): PresetReverb? = try {
        PresetReverb(0, sessionId).apply { enabled = false }
    } catch (_: Exception) { null }

    private fun tryCreateEq(sessionId: Int): Equalizer? = try {
        Equalizer(0, sessionId).apply { enabled = true }
    } catch (_: Exception) { null }

    private fun setupUI() {
        kickRow    = findViewById(R.id.kickRow)
        snareRow   = findViewById(R.id.snareRow)
        hatRow     = findViewById(R.id.hatRow)
        openHatRow = findViewById(R.id.openHatRow)
        clapRow    = findViewById(R.id.clapRow)

        buildRow(kickRow,    kickSteps,    kickStepsCount)
        buildRow(snareRow,   snareSteps,   snareStepsCount)
        buildRow(hatRow,     hatSteps,     hatStepsCount)
        buildRow(openHatRow, openHatSteps, openHatStepsCount)
        buildRow(clapRow,    clapSteps,    clapStepsCount)

        // Sync toggle button texts and row labels to current (possibly loaded) state
        findViewById<Button>(R.id.kickToggle).text    = kickStepsCount.toString()
        findViewById<Button>(R.id.snareToggle).text   = snareStepsCount.toString()
        findViewById<Button>(R.id.hatToggle).text     = hatStepsCount.toString()
        findViewById<Button>(R.id.openHatToggle).text = openHatStepsCount.toString()
        findViewById<Button>(R.id.clapToggle).text    = clapStepsCount.toString()

        updateRowLabel(R.id.kickLabel,    kickSoundResName)
        updateRowLabel(R.id.snareLabel,   snareSoundResName)
        updateRowLabel(R.id.hatLabel,     hatSoundResName)
        updateRowLabel(R.id.openHatLabel, openHatSoundResName)
        updateRowLabel(R.id.clapLabel,    clapSoundResName)

        // Swing slider
        val swingLabel = findViewById<TextView>(R.id.swingLabel)
        val swingBar   = findViewById<SeekBar>(R.id.swingBar)
        swingBar.progress = (swing * 100).toInt()
        swingLabel.text   = "${(swing * 100).toInt()}%"
        swingBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                swing = p / 100f
                swingLabel.text = "$p%"
                if (p > 30) AchievementManager.unlock(this@SecondActivity, "swing_master")
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // BPM controls
        val bpmDisplay = findViewById<TextView>(R.id.bpmDisplay)
        bpmDisplay.text = bpm.toString()

        findViewById<Button>(R.id.bpmMinus).setOnClickListener {
            if (bpm > 40) {
                bpm -= 5
                stepDuration = calcStepDuration(bpm)
                bpmDisplay.text = bpm.toString()
                hasUnsavedChanges = true
            }
        }
        findViewById<Button>(R.id.bpmPlus).setOnClickListener {
            if (bpm < 240) {
                bpm += 5
                stepDuration = calcStepDuration(bpm)
                bpmDisplay.text = bpm.toString()
                hasUnsavedChanges = true
                if (bpm >= 160) AchievementManager.unlock(this, "speed_demon")
            }
        }

        // Row toggles (8/16 steps)
        findViewById<Button>(R.id.kickToggle).setOnClickListener    { toggleRow(kickRow,    kickSteps,    "kick") }
        findViewById<Button>(R.id.snareToggle).setOnClickListener   { toggleRow(snareRow,   snareSteps,   "snare") }
        findViewById<Button>(R.id.hatToggle).setOnClickListener     { toggleRow(hatRow,     hatSteps,     "hat") }
        findViewById<Button>(R.id.openHatToggle).setOnClickListener { toggleRow(openHatRow, openHatSteps, "openHat") }
        findViewById<Button>(R.id.clapToggle).setOnClickListener    { toggleRow(clapRow,    clapSteps,    "clap") }

        // Row labels open sound library
        listOf(
            R.id.kickLabel    to "kick",
            R.id.snareLabel   to "snare",
            R.id.hatLabel     to "hat",
            R.id.openHatLabel to "openHat",
            R.id.clapLabel    to "clap"
        ).forEach { (viewId, rowKey) ->
            findViewById<TextView>(viewId).setOnClickListener {
                currentEditingRow = rowKey
                val currentRes = when (rowKey) {
                    "kick"    -> kickSoundResName
                    "snare"   -> snareSoundResName
                    "hat"     -> hatSoundResName
                    "openHat" -> openHatSoundResName
                    else      -> clapSoundResName
                }
                val intent = Intent(this, SoundLibraryActivity::class.java)
                intent.putExtra("rowName", rowKey.uppercase())
                intent.putExtra("currentResName", currentRes)
                intent.putExtra("userId", userId)
                soundPickerLauncher.launch(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        // FX / EQ buttons
        findViewById<Button>(R.id.reverbButton).setOnClickListener { showReverbDialog() }
        findViewById<Button>(R.id.eqButton).setOnClickListener { showEqDialog() }

        // Play button
        val playButton = findViewById<Button>(R.id.playButton)
        playButton.setOnClickListener {
            if (!isPlaying) {
                isPlaying = true
                playButton.text = "■"
                playButton.setBackgroundResource(R.drawable.bg_stop_btn)
                playButton.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120)
                    .withEndAction { playButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                    .start()
                startSequencer()
            } else {
                stopSequencer()
                playButton.text = "▶"
                playButton.setBackgroundResource(R.drawable.bg_play_btn)
                resetHighlights()
            }
        }

        // Generate buttons
        findViewById<Button>(R.id.randomizeBtn).setOnClickListener { randomizeBeat() }
        findViewById<Button>(R.id.aiGenerateBtn).setOnClickListener { aiGenerateBeat() }

        // Save button
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            showSaveDialog()
        }

        // Export & Share button
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            startExport()
        }

        // Back button
        findViewById<Button>(R.id.backButton).setOnClickListener {
            handleBack()
        }

        setupMuteAndVolume()
        setupVisualizer()
    }

    private fun setupMuteAndVolume() {
        data class RowControls(val muteId: Int, val volumeId: Int, val panId: Int, val cardId: Int, val key: String)

        val rows = listOf(
            RowControls(R.id.kickMute,    R.id.kickVolume,    R.id.kickPan,    R.id.kickCard,    "kick"),
            RowControls(R.id.snareMute,   R.id.snareVolume,   R.id.snarePan,   R.id.snareCard,   "snare"),
            RowControls(R.id.hatMute,     R.id.hatVolume,     R.id.hatPan,     R.id.hatCard,     "hat"),
            RowControls(R.id.openHatMute, R.id.openHatVolume, R.id.openHatPan, R.id.openHatCard, "openHat"),
            RowControls(R.id.clapMute,    R.id.clapVolume,    R.id.clapPan,    R.id.clapCard,    "clap")
        )

        rows.forEach { rc ->
            val muteBtn = findViewById<Button>(rc.muteId)
            val volBar  = findViewById<SeekBar>(rc.volumeId)
            val panBar  = findViewById<SeekBar>(rc.panId)
            val card    = findViewById<LinearLayout>(rc.cardId)

            muteBtn.setOnClickListener {
                val nowMuted = !muted[rc.key]!!
                muted[rc.key] = nowMuted
                if (nowMuted) {
                    muteBtn.setBackgroundResource(R.drawable.bg_stop_btn)
                    muteBtn.setTextColor(getColor(R.color.text_primary))
                    card.alpha = 0.4f
                } else {
                    muteBtn.setBackgroundResource(R.drawable.bg_btn_secondary)
                    muteBtn.setTextColor(getColor(R.color.text_muted))
                    card.alpha = 1f
                }
                muteBtn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70)
                    .withEndAction { muteBtn.animate().scaleX(1f).scaleY(1f).setDuration(70).start() }
                    .start()
            }

            volBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    volumes[rc.key] = progress / 100f
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })

            // Restore pan from loaded project data
            panBar.progress = ((pans[rc.key]!! + 1f) * 100f).toInt().coerceIn(0, 200)
            panBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    pans[rc.key] = (progress - 100) / 100f
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun calcStepDuration(bpm: Int) = (60000 / bpm) / 4

    private fun startSequencer() {
        sequencerHandler.post(object : Runnable {
            override fun run() {
                if (!isPlaying) return
                val step = currentStep
                triggerSounds(step)
                currentStep = (step + 1) % maxSteps
                uiHandler.post { highlightAllRows(step); updateVisualizer(step) }
                val delay = if (step % 2 == 0)
                    (stepDuration * (1f + swing)).toLong()
                else
                    (stepDuration * (1f - swing)).toLong()
                sequencerHandler.postDelayed(this, delay)
            }
        })
    }

    private fun stopSequencer() {
        isPlaying = false
        currentStep = 0
        sequencerHandler.removeCallbacksAndMessages(null)
    }

    private fun triggerSounds(step: Int) {
        fun resolvedStep(count: Int) = if (count == 8) step / 2 else step
        fun shouldFire(count: Int) = count == 16 || step % 2 == 0

        fun fire(key: String, instrument: Int, steps: BooleanArray, count: Int, resName: String) {
            if (!muted[key]!! && shouldFire(count) && steps[resolvedStep(count)]) {
                val settings = soundSettingsCache[resName] ?: SoundSettings()
                val vol = (volumes[key]!! * settings.level * 0.85f).coerceAtMost(0.85f)
                val pan = pans[key]!!.coerceIn(-1f, 1f)
                val leftVol  = (vol * if (pan >= 0f) 1f else 1f + pan).coerceIn(0f, 0.85f)
                val rightVol = (vol * if (pan <= 0f) 1f else 1f - pan).coerceIn(0f, 0.85f)
                soundPool.play(instrument, leftVol, rightVol, 1, 0, settings.pitch)
            }
        }
        fire("kick",    kickInstrument,    kickSteps,    kickStepsCount,    kickSoundResName)
        fire("snare",   snareInstrument,   snareSteps,   snareStepsCount,   snareSoundResName)
        fire("hat",     hatInstrument,     hatSteps,     hatStepsCount,     hatSoundResName)
        fire("openHat", openHatInstrument, openHatSteps, openHatStepsCount, openHatSoundResName)
        fire("clap",    clapInstrument,    clapSteps,    clapStepsCount,    clapSoundResName)
    }

    private fun highlightAllRows(step: Int) {
        highlightRow(kickRow,    kickStepsCount,    step)
        highlightRow(snareRow,   snareStepsCount,   step)
        highlightRow(hatRow,     hatStepsCount,     step)
        highlightRow(openHatRow, openHatStepsCount, step)
        highlightRow(clapRow,    clapStepsCount,    step)
    }

    private fun highlightRow(row: LinearLayout, stepCount: Int, step: Int) {
        val stepIndex = if (stepCount == 8) step / 2 else step
        for (i in 0 until row.childCount) {
            val btn = row.getChildAt(i) as Button
            val active = btn.tag as Boolean
            btn.setBackgroundResource(when {
                i == stepIndex && active -> R.drawable.btn_step_playing_active
                i == stepIndex           -> R.drawable.btn_step_playing
                active                   -> R.drawable.btn_step_active
                else                     -> R.drawable.btn_step_normal
            })
            if (i == stepIndex) {
                btn.animate().scaleX(1.12f).scaleY(1.12f).setDuration(55)
                    .withEndAction { btn.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                    .start()
            }
        }
    }

    private fun resetHighlights() {
        listOf(kickRow, snareRow, hatRow, openHatRow, clapRow).forEach { row ->
            for (i in 0 until row.childCount) {
                val btn = row.getChildAt(i) as Button
                btn.setBackgroundResource(
                    if (btn.tag == true) R.drawable.btn_step_active else R.drawable.btn_step_normal
                )
            }
        }
    }

    private fun buildRow(row: LinearLayout, stateArray: BooleanArray, stepCount: Int) {
        row.removeAllViews()
        val heightPx = (46 * resources.displayMetrics.density).toInt()
        for (i in 0 until stepCount) {
            val btn = Button(this)
            val params = LinearLayout.LayoutParams(0, heightPx, 1f)
            params.setMargins(3, 3, 3, 3)
            btn.layoutParams = params
            btn.stateListAnimator = null
            btn.text = ""
            btn.tag = stateArray[i]
            btn.setBackgroundResource(
                if (stateArray[i]) R.drawable.btn_step_active else R.drawable.btn_step_normal
            )
            btn.setOnClickListener {
                val newState = !(btn.tag as Boolean)
                btn.tag = newState
                stateArray[i] = newState
                btn.setBackgroundResource(
                    if (newState) R.drawable.btn_step_active else R.drawable.btn_step_normal
                )
                btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                hasUnsavedChanges = true
                btn.animate().scaleX(0.82f).scaleY(0.82f).setDuration(70)
                    .withEndAction { btn.animate().scaleX(1f).scaleY(1f).setDuration(70).start() }
                    .start()
            }
            row.addView(btn)
        }
    }

    private fun toggleRow(row: LinearLayout, stateArray: BooleanArray, type: String) {
        val toggleBtn: Button
        when (type) {
            "kick" -> {
                kickStepsCount = if (kickStepsCount == 8) 16 else 8
                buildRow(row, stateArray, kickStepsCount)
                toggleBtn = findViewById(R.id.kickToggle)
                toggleBtn.text = kickStepsCount.toString()
            }
            "snare" -> {
                snareStepsCount = if (snareStepsCount == 8) 16 else 8
                buildRow(row, stateArray, snareStepsCount)
                toggleBtn = findViewById(R.id.snareToggle)
                toggleBtn.text = snareStepsCount.toString()
            }
            "hat" -> {
                hatStepsCount = if (hatStepsCount == 8) 16 else 8
                buildRow(row, stateArray, hatStepsCount)
                toggleBtn = findViewById(R.id.hatToggle)
                toggleBtn.text = hatStepsCount.toString()
            }
            "openHat" -> {
                openHatStepsCount = if (openHatStepsCount == 8) 16 else 8
                buildRow(row, stateArray, openHatStepsCount)
                toggleBtn = findViewById(R.id.openHatToggle)
                toggleBtn.text = openHatStepsCount.toString()
            }
            "clap" -> {
                clapStepsCount = if (clapStepsCount == 8) 16 else 8
                buildRow(row, stateArray, clapStepsCount)
                toggleBtn = findViewById(R.id.clapToggle)
                toggleBtn.text = clapStepsCount.toString()
            }
        }
    }

    private fun updateRowLabel(viewId: Int, resName: String) {
        findViewById<TextView>(viewId).text = soundDisplayNames[resName] ?: resName.uppercase()
    }

    private fun showSaveDialog() {
        val input = EditText(this).apply {
            hint = "Beat name"
            setText(loadedProjectName ?: "")
            setHintTextColor(ContextCompat.getColor(this@SecondActivity, R.color.text_muted))
            setTextColor(ContextCompat.getColor(this@SecondActivity, R.color.text_primary))
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Save Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "My Beat" }
                val rows = mapOf(
                    "kick"    to RowState(kickSteps,    kickStepsCount,    kickSoundResName,    pans["kick"]!!),
                    "snare"   to RowState(snareSteps,   snareStepsCount,   snareSoundResName,   pans["snare"]!!),
                    "hat"     to RowState(hatSteps,     hatStepsCount,     hatSoundResName,     pans["hat"]!!),
                    "openHat" to RowState(openHatSteps, openHatStepsCount, openHatSoundResName, pans["openHat"]!!),
                    "clap"    to RowState(clapSteps,    clapStepsCount,    clapSoundResName,    pans["clap"]!!)
                )
                val saved = ProjectManager.saveProject(this, userId, name, bpm, rows, swing)
                Toast.makeText(
                    this,
                    if (saved) "\"$name\" saved!" else "Save failed",
                    Toast.LENGTH_SHORT
                ).show()
                if (saved) {
                    loadedProjectName = name
                    hasUnsavedChanges = false
                    AchievementManager.unlock(this, "first_beat")
                    checkFullKitAchievement()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { enqueueExport() }

    private fun startExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enqueueExport()
        }
    }

    private fun enqueueExport() {
        val rows = mapOf(
            "kick"    to RowState(kickSteps,    kickStepsCount,    kickSoundResName,    pans["kick"]!!),
            "snare"   to RowState(snareSteps,   snareStepsCount,   snareSoundResName,   pans["snare"]!!),
            "hat"     to RowState(hatSteps,     hatStepsCount,     hatSoundResName,     pans["hat"]!!),
            "openHat" to RowState(openHatSteps, openHatStepsCount, openHatSoundResName, pans["openHat"]!!),
            "clap"    to RowState(clapSteps,    clapStepsCount,    clapSoundResName,    pans["clap"]!!)
        )
        val paramsFile = BeatExporter.writeParamsFile(
            context = this, userId = userId, bpm = bpm, swing = swing,
            barRows = List(4) { rows },
            volumes = volumes.toMap(), pans = pans.toMap(),
            muted = muted.toMap(), soundSettings = soundSettingsCache
        )
        val request = OneTimeWorkRequestBuilder<BeatExportWorker>()
            .setInputData(workDataOf(BeatExportWorker.KEY_PARAMS_FILE to paramsFile.absolutePath))
            .build()

        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Exporting in background…", Toast.LENGTH_SHORT).show()
        AchievementManager.unlock(this, "exporter")

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString(BeatExportWorker.KEY_OUTPUT_FILE)
                        if (path != null) shareFile(java.io.File(path))
                    }
                    WorkInfo.State.FAILED ->
                        Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }
    }

    private fun shareFile(file: java.io.File) {
        val uri = FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type  = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MeloWave Beat")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share beat via…"))
    }

    private fun loadProjectData(json: String) {
        try {
            val (loadedBpm, rows, _) = ProjectManager.parseRows(json)
            bpm = loadedBpm
            swing = ProjectManager.parseSwing(json)
            stepDuration = calcStepDuration(bpm)
            rows["kick"]?.let    { applyRow(it, kickSteps,    "kick") }
            rows["snare"]?.let   { applyRow(it, snareSteps,   "snare") }
            rows["hat"]?.let     { applyRow(it, hatSteps,     "hat") }
            rows["openHat"]?.let { applyRow(it, openHatSteps, "openHat") }
            rows["clap"]?.let    { applyRow(it, clapSteps,    "clap") }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load project", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyRow(state: RowState, stepsArray: BooleanArray, type: String) {
        state.steps.copyInto(stepsArray)
        val soundId = soundMap[state.soundResName]
        when (type) {
            "kick"    -> { kickStepsCount    = state.count; if (soundId != null) kickInstrument    = soundId; kickSoundResName    = state.soundResName; pans["kick"]    = state.pan }
            "snare"   -> { snareStepsCount   = state.count; if (soundId != null) snareInstrument   = soundId; snareSoundResName   = state.soundResName; pans["snare"]   = state.pan }
            "hat"     -> { hatStepsCount     = state.count; if (soundId != null) hatInstrument     = soundId; hatSoundResName     = state.soundResName; pans["hat"]     = state.pan }
            "openHat" -> { openHatStepsCount = state.count; if (soundId != null) openHatInstrument = soundId; openHatSoundResName = state.soundResName; pans["openHat"] = state.pan }
            "clap"    -> { clapStepsCount    = state.count; if (soundId != null) clapInstrument    = soundId; clapSoundResName    = state.soundResName; pans["clap"]    = state.pan }
        }
    }

    // ── Reverb ──────────────────────────────────────────────────────────────

    private val reverbPresetLabels = arrayOf("Off", "Small Room", "Medium Room", "Large Room", "Hall", "Large Hall", "Plate")
    private val reverbPresetValues = shortArrayOf(
        PresetReverb.PRESET_NONE, PresetReverb.PRESET_SMALLROOM, PresetReverb.PRESET_MEDIUMROOM,
        PresetReverb.PRESET_LARGEROOM, PresetReverb.PRESET_MEDIUMHALL, PresetReverb.PRESET_LARGEHALL,
        PresetReverb.PRESET_PLATE
    )

    private fun showReverbDialog() {
        val reverb = reverbEffect ?: run {
            Toast.makeText(this, "Reverb not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("audio_effects", MODE_PRIVATE)
        val current = prefs.getInt("reverb_preset_idx", 0)
        AlertDialog.Builder(this)
            .setTitle("Reverb")
            .setSingleChoiceItems(reverbPresetLabels, current) { _, idx ->
                applyReverbPreset(idx)
                prefs.edit().putInt("reverb_preset_idx", idx).apply()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun applyReverbPreset(idx: Int) {
        val reverb = reverbEffect ?: return
        if (idx == 0) {
            reverb.enabled = false
        } else {
            reverb.preset = reverbPresetValues.getOrElse(idx) { PresetReverb.PRESET_NONE }
            reverb.enabled = true
        }
    }

    // ── Equalizer ───────────────────────────────────────────────────────────

    private fun showEqDialog() {
        val eq = equalizerEffect ?: run {
            Toast.makeText(this, "EQ not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val numBands = eq.numberOfBands.toInt()
        val range    = eq.bandLevelRange          // [minMb, maxMb] in millibels
        val dp       = resources.displayMetrics.density
        val pad      = (20 * dp).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (12 * dp).toInt(), pad, 0)
            setBackgroundColor(getColor(R.color.bg_surface))
        }

        val bandSeekBars = mutableListOf<SeekBar>()

        for (band in 0 until numBands) {
            val freqMilliHz = eq.getCenterFreq(band.toShort())
            val freqLabel   = if (freqMilliHz >= 1_000_000) "${freqMilliHz / 1_000_000}kHz" else "${freqMilliHz / 1000}Hz"
            val currentMb   = eq.getBandLevel(band.toShort())
            val currentDb   = currentMb / 100

            val bandLabel = TextView(this).apply {
                text = "$freqLabel   ${if (currentDb >= 0) "+$currentDb" else "$currentDb"} dB"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(0, (10 * dp).toInt(), 0, 4)
            }
            layout.addView(bandLabel)

            val seekBar = SeekBar(this).apply {
                max      = range[1] - range[0]
                progress = (currentMb - range[0]).toInt()
                progressTintList = ColorStateList.valueOf(getColor(R.color.accent_cyan))
                thumbTintList    = ColorStateList.valueOf(getColor(R.color.accent_cyan))
                progressBackgroundTintList = ColorStateList.valueOf(getColor(R.color.step_inactive))
            }
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val mb = (range[0] + p).toShort()
                    eq.setBandLevel(band.toShort(), mb)
                    val db = mb / 100
                    bandLabel.text = "$freqLabel   ${if (db >= 0) "+$db" else "$db"} dB"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { saveEqLevels() }
            })
            layout.addView(seekBar)
            bandSeekBars.add(seekBar)
        }

        AlertDialog.Builder(this)
            .setTitle("Equalizer")
            .setView(layout)
            .setNeutralButton("Reset") { _, _ ->
                for (band in 0 until numBands) eq.setBandLevel(band.toShort(), 0)
                bandSeekBars.forEach { it.progress = (-range[0]).toInt() }
                saveEqLevels()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun saveEqLevels() {
        val eq = equalizerEffect ?: return
        val levels = (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toString() }.joinToString(",")
        getSharedPreferences("audio_effects", MODE_PRIVATE).edit().putString("eq_levels", levels).apply()
    }

    private fun restoreEqLevels(saved: String?) {
        saved ?: return
        val eq = equalizerEffect ?: return
        saved.split(",").forEachIndexed { band, mb ->
            if (band < eq.numberOfBands) eq.setBandLevel(band.toShort(), mb.trim().toShortOrNull() ?: 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sequencerHandler.removeCallbacksAndMessages(null)
        sequencerThread.quitSafely()
        uiHandler.removeCallbacksAndMessages(null)
        soundPool.release()
        reverbEffect?.release()
        equalizerEffect?.release()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onResume() {
        super.onResume()
        soundSettingsCache = SoundSettingsManager.getAll(this, userId)
    }

    private fun checkFullKitAchievement() {
        val allActive = listOf(kickSteps, snareSteps, hatSteps, openHatSteps, clapSteps)
            .all { arr -> arr.any { it } }
        if (allActive) AchievementManager.unlock(this, "full_kit")
    }

    // ── Live visualizer ──────────────────────────────────────────────────────

    private val visualizerBars = mutableListOf<View>()

    private fun setupVisualizer() {
        val container = findViewById<LinearLayout>(R.id.visualizerStrip)
        val dp = resources.displayMetrics.density
        val colors = listOf(
            getColor(R.color.accent_purple), getColor(R.color.accent_cyan),
            getColor(R.color.accent_amber),  getColor(R.color.accent_green)
        )
        repeat(16) { i ->
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, (20 * dp).toInt(), 1f)
                    .also { it.marginEnd = (2 * dp).toInt() }
                setBackgroundColor(colors[i % colors.size])
                alpha = 0.12f
            }
            visualizerBars.add(bar)
            container.addView(bar)
        }
    }

    private fun updateVisualizer(step: Int) {
        visualizerBars.forEachIndexed { i, bar ->
            val dist = minOf(Math.abs(i - step), 16 - Math.abs(i - step))
            val targetAlpha = when (dist) { 0 -> 1f; 1 -> 0.55f; 2 -> 0.3f; else -> 0.12f }
            bar.animate().alpha(targetAlpha).setDuration(if (dist == 0) 50L else 160L).start()
            if (dist == 0) bar.animate().scaleY(1.3f).setDuration(50)
                .withEndAction { bar.animate().scaleY(1f).setDuration(120).start() }.start()
        }
    }

    // ── Beat generation ───────────────────────────────────────────────────────

    private fun rebuildAllRows() {
        buildRow(kickRow,    kickSteps,    kickStepsCount)
        buildRow(snareRow,   snareSteps,   snareStepsCount)
        buildRow(hatRow,     hatSteps,     hatStepsCount)
        buildRow(openHatRow, openHatSteps, openHatStepsCount)
        buildRow(clapRow,    clapSteps,    clapStepsCount)
        findViewById<Button>(R.id.kickToggle).text    = kickStepsCount.toString()
        findViewById<Button>(R.id.snareToggle).text   = snareStepsCount.toString()
        findViewById<Button>(R.id.hatToggle).text     = hatStepsCount.toString()
        findViewById<Button>(R.id.openHatToggle).text = openHatStepsCount.toString()
        findViewById<Button>(R.id.clapToggle).text    = clapStepsCount.toString()
    }

    private fun randomizeBeat() {
        kickStepsCount = 16; snareStepsCount = 16; hatStepsCount = 16
        openHatStepsCount = 16; clapStepsCount = 16
        val density = listOf(0.2, 0.18, 0.35, 0.12, 0.15)
        listOf(kickSteps, snareSteps, hatSteps, openHatSteps, clapSteps)
            .forEachIndexed { i, arr -> arr.fill(false); repeat(16) { s -> arr[s] = Math.random() < density[i] } }
        rebuildAllRows()
        hasUnsavedChanges = true
        Toast.makeText(this, "Pattern randomized", Toast.LENGTH_SHORT).show()
    }

    private fun aiGenerateBeat() {
        val genres = listOf(
            "Hip-Hop" to mapOf(
                "kick"    to listOf(0, 4, 8, 11, 14),
                "snare"   to listOf(4, 12),
                "hat"     to listOf(2, 6, 8, 10, 14),
                "openHat" to listOf(7),
                "clap"    to listOf(4, 12)
            ),
            "House" to mapOf(
                "kick"    to listOf(0, 4, 8, 12),
                "snare"   to listOf(4, 12),
                "hat"     to listOf(0, 2, 4, 6, 8, 10, 12, 14),
                "openHat" to listOf(6, 14),
                "clap"    to emptyList()
            ),
            "Trap" to mapOf(
                "kick"    to listOf(0, 3, 8, 14),
                "snare"   to listOf(4, 12),
                "hat"     to (0 until 16).toList(),
                "openHat" to listOf(8),
                "clap"    to listOf(4, 12)
            ),
            "Techno" to mapOf(
                "kick"    to listOf(0, 4, 8, 12),
                "snare"   to listOf(2, 6, 10, 14),
                "hat"     to listOf(1, 3, 5, 7, 9, 11, 13, 15),
                "openHat" to listOf(4, 12),
                "clap"    to emptyList()
            )
        )
        val (genre, pattern) = genres.random()

        kickStepsCount = 16; snareStepsCount = 16; hatStepsCount = 16
        openHatStepsCount = 16; clapStepsCount = 16

        val arrays = mapOf("kick" to kickSteps, "snare" to snareSteps, "hat" to hatSteps,
            "openHat" to openHatSteps, "clap" to clapSteps)
        arrays.forEach { (_, arr) -> arr.fill(false) }
        pattern.forEach { (key, steps) ->
            val arr = arrays[key] ?: return@forEach
            steps.forEach { i -> if (i < 16) arr[i] = true }
            // slight randomisation: randomly flip 1 extra step
            val extra = (0 until 16).random()
            arr[extra] = !arr[extra]
        }

        rebuildAllRows()
        hasUnsavedChanges = true
        Toast.makeText(this, "$genre pattern generated", Toast.LENGTH_SHORT).show()
    }

    private fun handleBack() {
        if (!hasUnsavedChanges) {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Leave without saving?")
            .setPositiveButton("Leave") { _, _ ->
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            .setNegativeButton("Stay", null)
            .show()
    }

}
