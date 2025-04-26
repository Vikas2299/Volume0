package com.volume0.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppToggleAdapter
    private var permissionDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val titleTextView: TextView = findViewById(R.id.titleTextView)
        val title = "Volume0"
        val spannable = SpannableString(title)
        spannable.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 0, 6, 0)
        spannable.setSpan(ForegroundColorSpan(0xFF4A3C8C.toInt()), 6, 7, 0)
        titleTextView.text = spannable

        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        appAdapter = AppToggleAdapter(
            AppConfigManager.getAppConfigs(this),
            { app, isEnabled ->
                AppConfigManager.saveToggleState(this, app.packageName, isEnabled)
                sendToggleUpdateBroadcast()
            },
            { app, adVolume ->
                AppConfigManager.saveAdVolume(this, app.packageName, adVolume)
                sendToggleUpdateBroadcast()
            }
        )
        appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
        }

        // Observe LiveData for notification updates
        NotificationUpdateManager.notificationUpdate.observe(this, Observer { update ->
            Log.d("Volume0", "Received notification update: status=${update.status}, package=${update.packageName}")
//            Toast.makeText(this, "Song: ${update.status}", Toast.LENGTH_SHORT).show()
            appAdapter.updateSongInfo(update.packageName, update.status)
            appsRecyclerView.post { appsRecyclerView.adapter?.notifyDataSetChanged() }
        })

        NotificationUpdateManager.volumeUpdate.observe(this, Observer { update ->
            Log.d("Volume0", "Volume updated: ${update.volume}%")
        })

        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        if (!AdDetectorService.isNotificationServiceEnabled(this)) {
            showPermissionDialog()
        } else {
            permissionDialog?.dismiss()
        }
    }

    private fun showPermissionDialog() {
        permissionDialog?.dismiss()
        permissionDialog = AlertDialog.Builder(this)
            .setTitle("Notification Access Required")
            .setMessage("Volume0 needs notification access to detect ads and adjust volume. Please enable it in settings.")
            .setCancelable(false)
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .create()
        permissionDialog?.show()
        permissionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF000000.toInt())
    }


    private fun sendToggleUpdateBroadcast() {
        val intent = Intent("com.volume0.app.TOGGLE_UPDATE")
        sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        appAdapter.updateApps(AppConfigManager.getAppConfigs(this))
        checkAndRequestPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionDialog?.dismiss()
    }
}