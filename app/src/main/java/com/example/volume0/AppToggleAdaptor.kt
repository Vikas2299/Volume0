package com.example.volume0

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class AppToggleAdapter(
    private var apps: List<AppConfig>,
    private val onToggleChanged: (AppConfig, Boolean) -> Unit,
    private val onVolumeChanged: (AppConfig, Int) -> Unit
) : RecyclerView.Adapter<AppToggleAdapter.ViewHolder>() {

    private val songInfoMap = mutableMapOf<String, String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appNameTextView: TextView = view.findViewById(R.id.appNameTextView)
        val toggleSwitch: SwitchCompat = view.findViewById(R.id.toggleSwitch)
        val songInfoTextView: TextView = view.findViewById(R.id.songInfoTextView)
        val volumeSlider: SeekBar = view.findViewById(R.id.volumeSlider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_toggle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appNameTextView.text = "${app.name} (${app.adVolume}%)"
        holder.toggleSwitch.isChecked = app.isEnabled
        holder.volumeSlider.progress = app.adVolume

        // Set app icon based on package name
        when (app.packageName) {
            "com.spotify.music" -> holder.appIcon.setImageResource(R.drawable.spotify_logo)
            "com.soundcloud.android" -> holder.appIcon.setImageResource(R.drawable.soundcloud_logo)
        }

        holder.songInfoTextView.text = songInfoMap[app.packageName] ?: "No song playing"

        holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            app.isEnabled = isChecked
            onToggleChanged(app, isChecked)
        }

        holder.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    app.adVolume = progress
                    onVolumeChanged(app, progress)
                    holder.appNameTextView.text = "${app.name} (${app.adVolume}%)"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppConfig>) {
        apps = newApps
        notifyDataSetChanged()
    }

    fun updateSongInfo(packageName: String, songInfo: String) {
        songInfoMap[packageName] = songInfo
        val app = apps.find { it.packageName == packageName }
        app?.let {
            val position = apps.indexOf(it)
            if (position != -1) {
                notifyItemChanged(position)
            }
        }
    }
}