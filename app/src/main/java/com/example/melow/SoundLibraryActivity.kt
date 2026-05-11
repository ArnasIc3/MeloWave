package com.example.melow

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SoundLibraryActivity : AppCompatActivity() {

    private lateinit var soundPool: SoundPool
    private val loadedSounds = mutableMapOf<String, Int>()

    private val allSounds = listOf(
        SoundItem("Kick Drum",  "Drums",      "kick",    R.raw.kick),
        SoundItem("Snare Drum", "Drums",      "snare",   R.raw.snare),
        SoundItem("Hi-Hat",     "Cymbals",    "hihat",   R.raw.hihat),
        SoundItem("Open Hat",   "Cymbals",    "openhat", R.raw.openhat),
        SoundItem("Clap",       "Percussion", "clap",    R.raw.clap)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_library)

        val rowName = intent.getStringExtra("rowName") ?: ""
        val currentResName = intent.getStringExtra("currentResName") ?: ""

        findViewById<TextView>(R.id.editingRowLabel).text =
            if (rowName.isNotEmpty()) "Editing: $rowName" else ""

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttrs)
            .build()

        allSounds.forEach { sound ->
            loadedSounds[sound.resName] = soundPool.load(this, sound.resId, 1)
        }

        val adapter = SoundAdapter(
            sounds = allSounds,
            currentResName = currentResName,
            onPreview = { sound ->
                loadedSounds[sound.resName]?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
            },
            onSelect = { sound ->
                val result = Intent().apply {
                    putExtra("soundResName", sound.resName)
                    putExtra("soundDisplayName", sound.name)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        )

        findViewById<RecyclerView>(R.id.soundsRecycler).apply {
            layoutManager = LinearLayoutManager(this@SoundLibraryActivity)
            this.adapter = adapter
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
