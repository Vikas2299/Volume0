package com.example.volume0

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class AppToggleAdapter(
    private var apps: List<AppConfig>,
    private val onToggleChanged: (AppConfig, Boolean) -> Unit
) : RecyclerView.Adapter<AppToggleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appNameTextView: TextView = view.findViewById(R.id.appNameTextView)
        val toggleSwitch: SwitchCompat = view.findViewById(R.id.toggleSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_toggle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appNameTextView.text = app.name
        holder.toggleSwitch.isChecked = app.isEnabled
        holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            app.isEnabled = isChecked
            onToggleChanged(app, isChecked)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppConfig>) {
        apps = newApps
        notifyDataSetChanged()
    }
}