package com.example.melow

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ArrangementActivity : AppCompatActivity() {

    // ── State ─────────────────────────────────────────────────────────────────

    private var userId = -1L

    private val bars       = mutableListOf<BarEntry>()
    private val loadedBars = mutableListOf<Map<String, RowState>>()

    private var bpm   = 120
    private var swing = 0f

    private lateinit var soundPool: android.media.SoundPool
    private val soundMap          = mutableMapOf<String, Int>()
    private var soundSettingsCache = mapOf<String, SoundSettings>()

    private val sequencerThread = HandlerThread("ArrSeq").also { it.start() }
    private val sequencerHandler = Handler(sequencerThread.looper)
    private val uiHandler        = Handler(Looper.getMainLooper())
    @Volatile private var isPlaying   = false
    @Volatile private var currentStep = 0
    @Volatile private var currentBar  = 0

    private lateinit var adapter: BarSlotAdapter
    private lateinit var nowPlayingLabel: TextView
    private lateinit var playButton: Button

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arrangement)

        userId = intent.getLongExtra("userId", -1L)
        setupSoundPool()
        soundSettingsCache = SoundSettingsManager.getAll(this, userId)

        // Load saved arrangement
        ArrangementManager.load(this)?.let { saved ->
            bpm   = saved.bpm
            swing = saved.swing
            saved.bars.forEach { addBar(it, notify = false) }
        }

        setupUI()
    }

    private fun setupSoundPool() {
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(10).setAudioAttributes(attrs).build()

        // Pre-load built-in sounds
        mapOf("kick" to R.raw.kick, "snare" to R.raw.snare, "hihat" to R.raw.hihat,
              "openhat" to R.raw.openhat, "clap" to R.raw.clap).forEach { (k, id) ->
            soundMap[k] = soundPool.load(this, id, 1)
        }
        CustomSoundManager.listSounds(this, userId).forEach { s ->
            s.filePath?.let { soundMap[s.resName] = soundPool.load(it, 1) }
        }
    }

    private fun setupUI() {
        nowPlayingLabel = findViewById(R.id.nowPlayingLabel)
        playButton      = findViewById(R.id.playButton)

        // BPM
        val bpmDisplay = findViewById<TextView>(R.id.bpmDisplay)
        bpmDisplay.text = bpm.toString()
        findViewById<Button>(R.id.bpmMinus).setOnClickListener {
            if (bpm > 40) { bpm -= 5; bpmDisplay.text = bpm.toString() }
        }
        findViewById<Button>(R.id.bpmPlus).setOnClickListener {
            if (bpm < 240) { bpm += 5; bpmDisplay.text = bpm.toString() }
        }

        // Swing
        val swingLabel = findViewById<TextView>(R.id.swingLabel)
        val swingBar   = findViewById<SeekBar>(R.id.swingBar)
        swingBar.progress = (swing * 100).toInt()
        swingLabel.text   = "${(swing * 100).toInt()}%"
        swingBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                swing = p / 100f; swingLabel.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // RecyclerView
        adapter = BarSlotAdapter(bars) { pos -> confirmRemoveBar(pos) }
        findViewById<RecyclerView>(R.id.barRecycler).apply {
            layoutManager = LinearLayoutManager(this@ArrangementActivity)
            this.adapter  = this@ArrangementActivity.adapter
        }

        // Add bar
        findViewById<Button>(R.id.addBarButton).setOnClickListener { showPatternPicker() }

        // Play/stop
        playButton.setOnClickListener {
            if (isPlaying) stopPlayback() else startPlayback()
        }

        // Save
        findViewById<Button>(R.id.saveArrButton).setOnClickListener { saveArrangement() }

        // Export
        findViewById<Button>(R.id.exportArrButton).setOnClickListener {
            if (bars.isEmpty()) {
                Toast.makeText(this, "Add at least one bar", Toast.LENGTH_SHORT).show()
            } else exportArrangement()
        }

        // Back
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    // ── Bar management ────────────────────────────────────────────────────────

    private fun addBar(entry: BarEntry, notify: Boolean = true) {
        if (bars.size >= 32) {
            Toast.makeText(this, "Maximum 32 bars", Toast.LENGTH_SHORT).show()
            return
        }
        val json = ProjectManager.loadProjectJson(this, userId, entry.patternFileName) ?: return
        val (_, rows, _) = ProjectManager.parseRows(json)

        // Ensure all sounds in this pattern are loaded
        rows.values.forEach { rs ->
            if (!soundMap.containsKey(rs.soundResName)) {
                CustomSoundManager.fileForResName(this, userId, rs.soundResName)?.let { f ->
                    soundMap[rs.soundResName] = soundPool.load(f.absolutePath, 1)
                }
            }
        }

        bars.add(entry)
        loadedBars.add(rows)
        if (notify) adapter.notifyItemInserted(bars.size - 1)
    }

    private fun removeBar(pos: Int) {
        if (pos < 0 || pos >= bars.size) return
        bars.removeAt(pos)
        loadedBars.removeAt(pos)
        adapter.notifyItemRemoved(pos)
        adapter.notifyItemRangeChanged(pos, bars.size)
    }

    private fun confirmRemoveBar(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove bar ${pos + 1}?")
            .setPositiveButton("Remove") { _, _ -> removeBar(pos) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Pattern picker ────────────────────────────────────────────────────────

    private fun showPatternPicker() {
        val projects = ProjectManager.listProjects(this, userId)
        if (projects.isEmpty()) {
            Toast.makeText(this, "No saved patterns. Create and save a beat first.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = projects.map { "${it.name}  (${it.bpm} BPM)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose Pattern")
            .setItems(labels) { _, idx ->
                addBar(BarEntry(projects[idx].fileName, projects[idx].name))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Sequencer ─────────────────────────────────────────────────────────────

    private fun calcStepDuration() = (60_000 / bpm) / 4

    private fun startPlayback() {
        if (bars.isEmpty()) {
            Toast.makeText(this, "Add bars first", Toast.LENGTH_SHORT).show()
            return
        }
        isPlaying   = true
        currentStep = 0
        currentBar  = 0
        playButton.text = "■"
        playButton.setBackgroundResource(R.drawable.bg_stop_btn)

        sequencerHandler.post(object : Runnable {
            override fun run() {
                if (!isPlaying) return
                val step = currentStep
                val bar  = currentBar
                triggerBar(step, bar)
                uiHandler.post { updateNowPlaying(bar) }

                val nextStep = step + 1
                if (nextStep >= 16) {
                    val nextBar = bar + 1
                    if (nextBar >= bars.size) {
                        // End of arrangement
                        isPlaying = false
                        uiHandler.post { onPlaybackFinished() }
                        return
                    }
                    currentStep = 0
                    currentBar  = nextBar
                } else {
                    currentStep = nextStep
                }

                val stepDuration = calcStepDuration()
                val delay = if (step % 2 == 0)
                    (stepDuration * (1f + swing)).toLong()
                else
                    (stepDuration * (1f - swing)).toLong()
                sequencerHandler.postDelayed(this, delay)
            }
        })
    }

    private fun stopPlayback() {
        isPlaying = false
        sequencerHandler.removeCallbacksAndMessages(null)
        playButton.text = "▶"
        playButton.setBackgroundResource(R.drawable.bg_play_btn)
        nowPlayingLabel.text = ""
    }

    private fun onPlaybackFinished() {
        playButton.text = "▶"
        playButton.setBackgroundResource(R.drawable.bg_play_btn)
        nowPlayingLabel.text = ""
    }

    private fun updateNowPlaying(bar: Int) {
        val name = bars.getOrNull(bar)?.patternName ?: return
        nowPlayingLabel.text = "▶  Bar ${bar + 1}: $name"
    }

    private fun triggerBar(step: Int, barIdx: Int) {
        val rows = loadedBars.getOrNull(barIdx) ?: return
        val rowKeys = listOf("kick", "snare", "hat", "openHat", "clap")
        rowKeys.forEach { key ->
            val rs    = rows[key] ?: return@forEach
            val count = rs.count
            if (count == 8 && step % 2 != 0) return@forEach
            val resolvedStep = if (count == 8) step / 2 else step
            if (!rs.steps[resolvedStep]) return@forEach
            val soundId = soundMap[rs.soundResName] ?: return@forEach
            val settings  = soundSettingsCache[rs.soundResName] ?: SoundSettings()
            val vol       = (settings.level * 0.85f).coerceAtMost(0.85f)
            val pan       = rs.pan.coerceIn(-1f, 1f)
            val leftVol   = (vol * if (pan >= 0f) 1f else 1f + pan).coerceIn(0f, 0.85f)
            val rightVol  = (vol * if (pan <= 0f) 1f else 1f - pan).coerceIn(0f, 0.85f)
            soundPool.play(soundId, leftVol, rightVol, 1, 0, settings.pitch)
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun saveArrangement() {
        val data = ArrangementSave("My Arrangement", bpm, swing, bars.toList())
        val ok   = ArrangementManager.save(this, data)
        Toast.makeText(this, if (ok) "Arrangement saved" else "Save failed", Toast.LENGTH_SHORT).show()
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private fun exportArrangement() {
        val dp       = resources.displayMetrics.density
        val layout   = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = android.view.Gravity.CENTER
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
            setBackgroundColor(getColor(R.color.bg_surface))
        }
        val statusText  = TextView(this).apply {
            text = "Rendering ${bars.size} bars…"
            textSize = 16f; gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.text_primary))
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; isIndeterminate = false
            progressTintList = ColorStateList.valueOf(getColor(R.color.accent_cyan))
        }
        layout.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (16 * dp).toInt() })
        layout.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Exporting")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()

        // Use flat volumes/pans/muted (arrangement uses per-RowState pan, no per-instrument override)
        val volumes = mapOf("kick" to 1f, "snare" to 1f, "hat" to 1f, "openHat" to 1f, "clap" to 1f)
        val pans    = mapOf("kick" to 0f, "snare" to 0f, "hat" to 0f, "openHat" to 0f, "clap" to 0f)
        val muted   = mapOf("kick" to false, "snare" to false, "hat" to false, "openHat" to false, "clap" to false)

        BeatExporter.exportArrangement(
            context      = this,
            userId       = userId,
            bpm          = bpm,
            swing        = swing,
            barRows      = loadedBars.toList(),
            volumes      = volumes,
            pans         = pans,
            muted        = muted,
            soundSettings = soundSettingsCache,
            onProgress   = { pct -> uiHandler.post { progressBar.progress = pct } },
            onDone       = { file ->
                uiHandler.post {
                    dialog.dismiss()
                    if (file != null) shareFile(file)
                    else Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun shareFile(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share arrangement via…"
        ))
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        soundSettingsCache = SoundSettingsManager.getAll(this, userId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        sequencerHandler.removeCallbacksAndMessages(null)
        sequencerThread.quitSafely()
        soundPool.release()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class BarSlotAdapter(
        private val items: MutableList<BarEntry>,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<BarSlotAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val barNumber:   TextView = view.findViewById(R.id.barNumber)
            val patternName: TextView = view.findViewById(R.id.patternName)
            val patternMeta: TextView = view.findViewById(R.id.patternMeta)
            val removeBtn:   Button   = view.findViewById(R.id.removeBarBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_bar_slot, parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = items[position]
            holder.barNumber.text   = "${position + 1}"
            holder.patternName.text = entry.patternName

            // Show BPM from loaded bar if available
            val rows = loadedBars.getOrNull(position)
            holder.patternMeta.text = if (rows != null) {
                val stepCounts = rows.values.map { it.count }.distinct()
                val stepsStr   = if (stepCounts.size == 1) "${stepCounts[0]} steps" else "mixed steps"
                stepsStr
            } else ""

            // Highlight currently playing bar
            val isPlaying = this@ArrangementActivity.isPlaying && currentBar == position
            holder.itemView.alpha = if (isPlaying) 1f else 0.85f

            holder.removeBtn.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
        }

        override fun getItemCount() = items.size
    }
}
