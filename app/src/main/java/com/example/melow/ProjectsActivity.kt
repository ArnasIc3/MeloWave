package com.example.melow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProjectsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: ProjectAdapter
    private val projects = mutableListOf<ProjectInfo>()
    private var userId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_projects)
        userId = intent.getLongExtra("userId", -1L)

        recycler = findViewById(R.id.projectsRecycler)
        emptyState = findViewById(R.id.emptyState)

        adapter = ProjectAdapter(
            projects,
            onLoad = { project ->
                val json = ProjectManager.loadProjectJson(this, userId, project.fileName) ?: return@ProjectAdapter
                val intent = Intent(this, SecondActivity::class.java)
                intent.putExtra("projectJson", json)
                intent.putExtra("projectName", project.name)
                intent.putExtra("userId", userId)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onDelete = { project, position ->
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${project.name}\"?")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        ProjectManager.deleteProject(this, userId, project.fileName)
                        adapter.removeAt(position)
                        updateEmptyState()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        loadProjects()
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }

    private fun loadProjects() {
        projects.clear()
        projects.addAll(ProjectManager.listProjects(this, userId))
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (projects.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
