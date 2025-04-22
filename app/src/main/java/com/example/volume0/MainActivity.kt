package com.example.volume0

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppToggleAdapter
    private var permissionDialog: AlertDialog? = null

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "No data"
            val packageName = intent?.getStringExtra("packageName") ?: ""
            appAdapter.updateSongInfo(packageName, status)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the title with different colors for "Volume" and "0"
        val titleTextView: TextView = findViewById(R.id.titleTextView)
        val title = "Volume0"
        val spannable = SpannableString(title)
        spannable.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 0, 6, 0) // "Volume" in white
        spannable.setSpan(ForegroundColorSpan(0xFF4A3C8C.toInt()), 6, 7, 0) // "0" in purple
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

        val notificationFilter = IntentFilter("com.example.volume0.NOTIFICATION_UPDATE")
        ContextCompat.registerReceiver(this, notificationReceiver, notificationFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Show permission dialog if not granted
        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        if (!isNotificationServiceEnabled()) {
            showPermissionDialog()
        } else {
            permissionDialog?.dismiss()
        }
    }

    private fun showPermissionDialog() {
        permissionDialog?.dismiss()
        permissionDialog = AlertDialog.Builder(this)
            .setTitle("Notification Access Required")
            .setMessage("This app needs notification access to detect ads and adjust volume. Please enable it in settings.")
            .setCancelable(false)
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .create()
        permissionDialog?.show()

        permissionDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF000000.toInt())

    }

    private fun sendToggleUpdateBroadcast() {
        val intent = Intent("com.example.volume0.TOGGLE_UPDATE")
        sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        appAdapter.updateApps(AppConfigManager.getAppConfigs(this))
        checkAndRequestPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
        permissionDialog?.dismiss()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }
}