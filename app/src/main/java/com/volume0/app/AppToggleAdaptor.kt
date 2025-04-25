package com.volume0.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import android.util.Log

class AppToggleAdapter(
    private var apps: List<AppConfig>,
    private val onToggleChanged: (AppConfig, Boolean) -> Unit,
    private val onVolumeChanged: (AppConfig, Int) -> Unit
) : RecyclerView.Adapter<AppToggleAdapter.AppViewHolder>() {

    private val songInfoMap = mutableMapOf<String, String>()

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        val toggleSwitch: SwitchCompat = itemView.findViewById(R.id.toggleSwitch)
        val songInfoTextView: TextView = itemView.findViewById(R.id.songInfoTextView)
        val volumeSlider: SeekBar = itemView.findViewById(R.id.volumeSlider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_toggle, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
//        holder.appNameTextView.text = app.name
        holder.appNameTextView.text = "${app.name} (${app.adVolume}%)"
        when (app.packageName) {
            "com.spotify.music" -> holder.appIcon.setImageResource(R.drawable.spotify_logo)
            "com.soundcloud.android" -> holder.appIcon.setImageResource(R.drawable.soundcloud_logo)
        }
        holder.toggleSwitch.isChecked = app.isEnabled
        holder.volumeSlider.progress = app.adVolume
        holder.volumeSlider.isEnabled = app.isEnabled
        holder.songInfoTextView.text = songInfoMap[app.packageName] ?: "No song playing"
//        holder.volumeLabelTextView.text = "Volume during ads (${app.adVolume}%):"

        holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleChanged(app, isChecked)
            holder.volumeSlider.isEnabled = isChecked
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
        Log.d("Volume0", "Updating song info: $packageName, $songInfo")
        songInfoMap[packageName] = songInfo
        val app = apps.find { it.packageName == packageName }
        app?.let {
            val position = apps.indexOf(it)
            if (position != -1) {
                notifyItemChanged(position)
            } else {
                Log.d("Volume0", "App not found for $packageName")
            }
        } ?: Log.d("Volume0", "App not found for $packageName, notifying all")
        notifyDataSetChanged()
    }
}