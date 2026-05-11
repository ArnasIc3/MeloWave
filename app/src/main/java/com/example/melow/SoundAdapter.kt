package com.example.melow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class SoundItem(val name: String, val category: String, val resName: String, val resId: Int)

class SoundAdapter(
    private val sounds: List<SoundItem>,
    private val currentResName: String,
    private val onPreview: (SoundItem) -> Unit,
    private val onSelect: (SoundItem) -> Unit
) : RecyclerView.Adapter<SoundAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.soundName)
        val category: TextView = view.findViewById(R.id.soundCategory)
        val previewBtn: Button = view.findViewById(R.id.previewBtn)
        val selectBtn: Button = view.findViewById(R.id.selectBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sound, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sound = sounds[position]
        holder.name.text = sound.name
        holder.category.text = sound.category

        val isSelected = sound.resName == currentResName
        holder.selectBtn.text = if (isSelected) "Active" else "Select"
        holder.selectBtn.alpha = if (isSelected) 0.6f else 1f

        holder.previewBtn.setOnClickListener {
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()
            onPreview(sound)
        }

        holder.selectBtn.setOnClickListener {
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()
            onSelect(sound)
        }
    }

    override fun getItemCount() = sounds.size
}
