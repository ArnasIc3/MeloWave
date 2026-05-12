package com.example.melow

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

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
    private val muted = mutableMapOf("kick" to false, "snare" to false, "hat" to false, "openHat" to false, "clap" to false)
    private val volumes = mutableMapOf("kick" to 1f, "snare" to 1f, "hat" to 1f, "openHat" to 1f, "clap" to 1f)

    private val soundDisplayNames = mapOf(
        "kick"    to "Kick Drum",
        "snare"   to "Snare Drum",
        "hihat"   to "Hi-Hat",
        "openhat" to "Open Hat",
        "clap"    to "Clap"
    )

    private val sequencerThread = android.os.HandlerThread("SequencerThread").also { it.start() }
    private val sequencerHandler = Handler(sequencerThread.looper)
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile private var currentStep = 0
    @Volatile private var isPlaying = false

    private var bpm = 120
    private var stepDuration = calcStepDuration(120)

    private var loadedProjectName: String? = null

    private var currentEditingRow = ""

    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resName = result.data?.getStringExtra("soundResName") ?: return@registerForActivityResult
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

        setupSoundPool()
        // Load data into arrays BEFORE building UI so buttons reflect saved state
        intent.getStringExtra("projectJson")?.let { loadProjectData(it) }
        loadedProjectName = intent.getStringExtra("projectName")
        setupUI()
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
    }

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

        // BPM controls
        val bpmDisplay = findViewById<TextView>(R.id.bpmDisplay)
        bpmDisplay.text = bpm.toString()

        findViewById<Button>(R.id.bpmMinus).setOnClickListener {
            if (bpm > 40) {
                bpm -= 5
                stepDuration = calcStepDuration(bpm)
                bpmDisplay.text = bpm.toString()
            }
        }
        findViewById<Button>(R.id.bpmPlus).setOnClickListener {
            if (bpm < 240) {
                bpm += 5
                stepDuration = calcStepDuration(bpm)
                bpmDisplay.text = bpm.toString()
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
                soundPickerLauncher.launch(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

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

        // Save button
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            showSaveDialog()
        }

        // Back button
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupMuteAndVolume()
    }

    private fun setupMuteAndVolume() {
        data class RowControls(val muteId: Int, val volumeId: Int, val cardId: Int, val key: String)

        val rows = listOf(
            RowControls(R.id.kickMute,    R.id.kickVolume,    R.id.kickCard,    "kick"),
            RowControls(R.id.snareMute,   R.id.snareVolume,   R.id.snareCard,   "snare"),
            RowControls(R.id.hatMute,     R.id.hatVolume,     R.id.hatCard,     "hat"),
            RowControls(R.id.openHatMute, R.id.openHatVolume, R.id.openHatCard, "openHat"),
            RowControls(R.id.clapMute,    R.id.clapVolume,    R.id.clapCard,    "clap")
        )

        rows.forEach { rc ->
            val muteBtn = findViewById<Button>(rc.muteId)
            val seekBar = findViewById<SeekBar>(rc.volumeId)
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

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    volumes[rc.key] = progress / 100f
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
                uiHandler.post { highlightAllRows(step) }
                sequencerHandler.postDelayed(this, stepDuration.toLong())
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

        fun fire(key: String, instrument: Int, steps: BooleanArray, count: Int) {
            if (!muted[key]!! && shouldFire(count) && steps[resolvedStep(count)]) {
                val v = (volumes[key]!! * 0.85f).coerceAtMost(0.85f)
                soundPool.play(instrument, v, v, 1, 0, 1f)
            }
        }
        fire("kick",    kickInstrument,    kickSteps,    kickStepsCount)
        fire("snare",   snareInstrument,   snareSteps,   snareStepsCount)
        fire("hat",     hatInstrument,     hatSteps,     hatStepsCount)
        fire("openHat", openHatInstrument, openHatSteps, openHatStepsCount)
        fire("clap",    clapInstrument,    clapSteps,    clapStepsCount)
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
            val drawableRes = when {
                i == stepIndex && active -> R.drawable.btn_step_playing_active
                i == stepIndex           -> R.drawable.btn_step_playing
                active                   -> R.drawable.btn_step_active
                else                     -> R.drawable.btn_step_normal
            }
            btn.setBackgroundResource(drawableRes)
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
                    "kick"    to RowState(kickSteps,    kickStepsCount,    kickSoundResName),
                    "snare"   to RowState(snareSteps,   snareStepsCount,   snareSoundResName),
                    "hat"     to RowState(hatSteps,     hatStepsCount,     hatSoundResName),
                    "openHat" to RowState(openHatSteps, openHatStepsCount, openHatSoundResName),
                    "clap"    to RowState(clapSteps,    clapStepsCount,    clapSoundResName)
                )
                val saved = ProjectManager.saveProject(this, name, bpm, rows)
                Toast.makeText(
                    this,
                    if (saved) "\"$name\" saved!" else "Save failed",
                    Toast.LENGTH_SHORT
                ).show()
                if (saved) loadedProjectName = name
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProjectData(json: String) {
        try {
            val (loadedBpm, rows, _) = ProjectManager.parseRows(json)
            bpm = loadedBpm
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
            "kick"    -> { kickStepsCount    = state.count; if (soundId != null) kickInstrument    = soundId; kickSoundResName    = state.soundResName }
            "snare"   -> { snareStepsCount   = state.count; if (soundId != null) snareInstrument   = soundId; snareSoundResName   = state.soundResName }
            "hat"     -> { hatStepsCount     = state.count; if (soundId != null) hatInstrument     = soundId; hatSoundResName     = state.soundResName }
            "openHat" -> { openHatStepsCount = state.count; if (soundId != null) openHatInstrument = soundId; openHatSoundResName = state.soundResName }
            "clap"    -> { clapStepsCount    = state.count; if (soundId != null) clapInstrument    = soundId; clapSoundResName    = state.soundResName }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sequencerHandler.removeCallbacksAndMessages(null)
        sequencerThread.quitSafely()
        uiHandler.removeCallbacksAndMessages(null)
        soundPool.release()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
