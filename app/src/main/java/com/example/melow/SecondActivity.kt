package com.example.melow

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.View

class SecondActivity : AppCompatActivity() {

    private val maxSteps = 16

    private var kickStepsCount = 8
    private var snareStepsCount = 8
    private var hatStepsCount = 8
    private var openHatStepsCount = 8
    private var clapStepsCount = 8

    private lateinit var soundPool: SoundPool

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

    private val handler = Handler(Looper.getMainLooper())
    private var currentStep = 0
    private var isPlaying = false

    private var bpm = 120
    private var stepDuration = (60000 / bpm) / 2

    private val instruments = arrayOf(
        "Kick",
        "Snare",
        "HiHat",
        "Clap",
        "Open Hat"
    )

    private val instrumentMap = HashMap<String, Int>()

    private var kickInstrument = 0
    private var snareInstrument = 0
    private var hatInstrument = 0
    private var openHatInstrument = 0
    private var clapInstrument = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        val bpmInput = findViewById<EditText>(R.id.bpmInput)

        bpmInput.setOnEditorActionListener { _, _, _ ->

            val bpmValue = bpmInput.text.toString().toIntOrNull()

            if (bpmValue != null && bpmValue > 0) {
                bpm = bpmValue
                stepDuration = (60000 / bpm) / 4
            }

            false
        }

        // SOUNDPOOL

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        kickSound = soundPool.load(this, R.raw.kick, 1)
        snareSound = soundPool.load(this, R.raw.snare, 1)
        hatSound = soundPool.load(this, R.raw.hihat, 1)
        openHatSound = soundPool.load(this, R.raw.openhat, 1)
        clapSound = soundPool.load(this, R.raw.clap, 1)

        instrumentMap["Kick"] = kickSound
        instrumentMap["Snare"] = snareSound
        instrumentMap["HiHat"] = hatSound
        instrumentMap["Clap"] = clapSound
        instrumentMap["Open Hat"] = openHatSound

        kickInstrument = kickSound
        snareInstrument = snareSound
        hatInstrument = hatSound
        openHatInstrument = openHatSound
        clapInstrument = clapSound

        // UI

        kickRow = findViewById(R.id.kickRow)
        snareRow = findViewById(R.id.snareRow)
        hatRow = findViewById(R.id.hatRow)
        openHatRow = findViewById(R.id.openHatRow)
        clapRow = findViewById(R.id.clapRow)

        buildRow(kickRow, kickSteps, kickStepsCount)
        buildRow(snareRow, snareSteps, snareStepsCount)
        buildRow(hatRow, hatSteps, hatStepsCount)
        buildRow(openHatRow, openHatSteps, openHatStepsCount)
        buildRow(clapRow, clapSteps, clapStepsCount)

        setupSpinner(findViewById(R.id.kickSpinner), "kick")
        setupSpinner(findViewById(R.id.snareSpinner), "snare")
        setupSpinner(findViewById(R.id.hatSpinner), "hat")
        setupSpinner(findViewById(R.id.openHatSpinner), "openHat")
        setupSpinner(findViewById(R.id.clapSpinner), "clap")

        val playButton = findViewById<Button>(R.id.playButton)

        playButton.setOnClickListener {

            if (!isPlaying) {
                isPlaying = true
                startSequencer()
                playButton.text = "STOP"
            } else {
                isPlaying = false
                playButton.text = "PLAY"
            }

        }

        findViewById<Button>(R.id.kickToggle).setOnClickListener { toggleRow(kickRow, kickSteps, "kick") }
        findViewById<Button>(R.id.snareToggle).setOnClickListener { toggleRow(snareRow, snareSteps, "snare") }
        findViewById<Button>(R.id.hatToggle).setOnClickListener { toggleRow(hatRow, hatSteps, "hat") }
        findViewById<Button>(R.id.openHatToggle).setOnClickListener { toggleRow(openHatRow, openHatSteps, "openHat") }
        findViewById<Button>(R.id.clapToggle).setOnClickListener { toggleRow(clapRow, clapSteps, "clap") }

    }

    private fun startSequencer() {

        handler.post(object : Runnable {

            override fun run() {

                if (!isPlaying) return

                playStep()

                currentStep++
                if (currentStep >= maxSteps) currentStep = 0

                handler.postDelayed(this, stepDuration.toLong())
            }

        })
    }

    private fun playStep() {

    highlightStep(kickRow)
    highlightStep(snareRow)
    highlightStep(hatRow)
    highlightStep(openHatRow)
    highlightStep(clapRow)

    val kickStep = if (kickStepsCount == 8) currentStep / 2 else currentStep
    val snareStep = if (snareStepsCount == 8) currentStep / 2 else currentStep
    val hatStep = if (hatStepsCount == 8) currentStep / 2 else currentStep
    val openHatStep = if (openHatStepsCount == 8) currentStep / 2 else currentStep
    val clapStep = if (clapStepsCount == 8) currentStep / 2 else currentStep

    if (kickSteps[kickStep])
        soundPool.play(kickInstrument, 1f, 1f, 1, 0, 1f)

    if (snareSteps[snareStep])
        soundPool.play(snareInstrument, 1f, 1f, 1, 0, 1f)

    if (hatSteps[hatStep])
        soundPool.play(hatInstrument, 1f, 1f, 1, 0, 1f)

    if (openHatSteps[openHatStep])
        soundPool.play(openHatInstrument, 1f, 1f, 1, 0, 1f)

    if (clapSteps[clapStep])
        soundPool.play(clapInstrument, 1f, 1f, 1, 0, 1f)
    }

    private fun highlightStep(row: LinearLayout) {

    val stepIndex =
        if (row.childCount == 8) currentStep / 2
        else currentStep

    for (i in 0 until row.childCount) {

        val button = row.getChildAt(i) as Button

        if (i == stepIndex) {

            if (button.tag == true)
                button.setBackgroundColor(Color.YELLOW)
            else
                button.setBackgroundColor(Color.GRAY)

        } else {

            if (button.tag == true)
                button.setBackgroundColor(Color.GREEN)
            else
                button.setBackgroundColor(Color.DKGRAY)
        }
    }
    }

    private fun buildRow(row: LinearLayout, stateArray: BooleanArray, stepCount: Int) {

        row.removeAllViews()

        for (i in 0 until stepCount) {

            val button = Button(this)

            val params = LinearLayout.LayoutParams(0, 90, 1f)
            params.setMargins(4,4,4,4)

            button.layoutParams = params
            button.setBackgroundColor(Color.DKGRAY)

            button.tag = false

            button.setOnClickListener {

                val active = button.tag as Boolean
                val newState = !active

                button.tag = newState
                stateArray[i] = newState

                if (newState)
                    button.setBackgroundColor(Color.GREEN)
                else
                    button.setBackgroundColor(Color.DKGRAY)
            }

            row.addView(button)
        }
    }

    private fun toggleRow(row: LinearLayout, stateArray: BooleanArray, type: String) {

        when(type) {
            "kick" -> {
                kickStepsCount = if (kickStepsCount == 8) 16 else 8
                buildRow(row, stateArray, kickStepsCount)
            }

            "snare" -> {
                snareStepsCount = if (snareStepsCount == 8) 16 else 8
                buildRow(row, stateArray, snareStepsCount)
            }

            "hat" -> {
                hatStepsCount = if (hatStepsCount == 8) 16 else 8
                buildRow(row, stateArray, hatStepsCount)
            }

            "openHat" -> {
                openHatStepsCount = if (openHatStepsCount == 8) 16 else 8
                buildRow(row, stateArray, openHatStepsCount)
            }

            "clap" -> {
                clapStepsCount = if (clapStepsCount == 8) 16 else 8
                buildRow(row, stateArray, clapStepsCount)
            }
        }
    }

    private fun setupSpinner(spinner: Spinner, row: String) {

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            instruments
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {

                val instrumentName = instruments[position]
                val soundId = instrumentMap[instrumentName] ?: return

                when(row) {
                    "kick" -> kickInstrument = soundId
                    "snare" -> snareInstrument = soundId
                    "hat" -> hatInstrument = soundId
                    "openHat" -> openHatInstrument = soundId
                    "clap" -> clapInstrument = soundId
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}