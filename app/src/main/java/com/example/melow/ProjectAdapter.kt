package com.example.melow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProjectAdapter(
    private val projects: MutableList<ProjectInfo>,
    private val onLoad: (ProjectInfo) -> Unit,
    private val onDelete: (ProjectInfo, Int) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.projectName)
        val meta: TextView = view.findViewById(R.id.projectMeta)
        val date: TextView = view.findViewById(R.id.projectDate)
        val loadBtn: Button = view.findViewById(R.id.loadBtn)
        val deleteBtn: Button = view.findViewById(R.id.deleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val project = projects[position]
        holder.name.text = project.name
        holder.meta.text = "${project.bpm} BPM"
        holder.date.text = project.formattedDate()
        holder.loadBtn.setOnClickListener {
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()
            onLoad(project)
        }
        holder.deleteBtn.setOnClickListener {
            onDelete(project, holder.adapterPosition)
        }
    }

    override fun getItemCount() = projects.size

    fun removeAt(position: Int) {
        projects.removeAt(position)
        notifyItemRemoved(position)
    }
}
