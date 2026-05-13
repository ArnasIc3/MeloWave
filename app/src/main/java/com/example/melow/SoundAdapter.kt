package com.example.melow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class SoundItem(
    val name: String,
    val category: String,
    val resName: String,
    val resId: Int,
    val filePath: String? = null
) {
    val isCustom get() = filePath != null
}

class SoundAdapter(
    private val sounds: MutableList<SoundItem>,
    private val currentResName: String,
    private val onPreview: (SoundItem) -> Unit,
    private val onSelect: (SoundItem) -> Unit,
    private val onEdit: (SoundItem) -> Unit,
    private val onDelete: ((SoundItem, Int) -> Unit)? = null
) : RecyclerView.Adapter<SoundAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView      = view.findViewById(R.id.soundName)
        val category: TextView  = view.findViewById(R.id.soundCategory)
        val previewBtn: Button  = view.findViewById(R.id.previewBtn)
        val selectBtn: Button   = view.findViewById(R.id.selectBtn)
        val editBtn: Button     = view.findViewById(R.id.editSoundBtn)
        val deleteBtn: Button   = view.findViewById(R.id.deleteSoundBtn)
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

        holder.deleteBtn.visibility = if (sound.isCustom) View.VISIBLE else View.GONE

        holder.previewBtn.setOnClickListener {
            animateClick(it) { onPreview(sound) }
        }
        holder.selectBtn.setOnClickListener {
            animateClick(it) { onSelect(sound) }
        }
        holder.editBtn.setOnClickListener {
            animateClick(it) { onEdit(sound) }
        }
        holder.deleteBtn.setOnClickListener {
            onDelete?.invoke(sound, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount() = sounds.size

    fun removeAt(position: Int) {
        sounds.removeAt(position)
        notifyItemRemoved(position)
    }

    fun updateAt(position: Int, item: SoundItem) {
        sounds[position] = item
        notifyItemChanged(position)
    }

    private fun animateClick(view: View, action: () -> Unit) {
        view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(75)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(75)
                .withEndAction(action).start() }
            .start()
    }
}
