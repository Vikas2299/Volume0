package com.example.volume0

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var permissionButton: Button
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appAdapter: AppToggleAdapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "No data"
            statusTextView.text = status
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        permissionButton = findViewById(R.id.permissionButton)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)

        // Setup RecyclerView for toggles
        appAdapter = AppToggleAdapter(AppConfigManager.getAppConfigs(this)) { app, isEnabled ->
            AppConfigManager.saveToggleState(this, app.packageName, isEnabled)
            sendToggleUpdateBroadcast() // Notify service of toggle change
        }
        appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
        }

        // Register receiver for service updates
        val filter = IntentFilter("com.example.volume0.NOTIFICATION_UPDATE")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Handle permission button
        permissionButton.setOnClickListener {
            if (!isNotificationServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                permissionButton.text = getString(R.string.permission_granted)
                permissionButton.isEnabled = false
            }
        }

        updatePermissionButton()
    }

    private fun sendToggleUpdateBroadcast() {
        val intent = Intent("com.example.volume0.TOGGLE_UPDATE")
        sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButton()
        // Refresh toggles in case settings changed
        appAdapter.updateApps(AppConfigManager.getAppConfigs(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    private fun updatePermissionButton() {
        if (isNotificationServiceEnabled()) {
            permissionButton.text = getString(R.string.permission_granted)
            permissionButton.isEnabled = false
        } else {
            permissionButton.text = getString(R.string.grant_permission)
            permissionButton.isEnabled = true
        }
    }
}