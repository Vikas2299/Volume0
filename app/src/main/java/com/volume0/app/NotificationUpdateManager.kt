package com.volume0.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object NotificationUpdateManager {
    data class NotificationUpdate(val status: String, val packageName: String)
    data class VolumeUpdate(val volume: Int)

    private val _notificationUpdate = MutableLiveData<NotificationUpdate>()
    val notificationUpdate: LiveData<NotificationUpdate> = _notificationUpdate

    private val _volumeUpdate = MutableLiveData<VolumeUpdate>()
    val volumeUpdate: LiveData<VolumeUpdate> = _volumeUpdate

    fun postNotificationUpdate(status: String, packageName: String) {
        _notificationUpdate.postValue(NotificationUpdate(status, packageName))
    }

    fun postVolumeUpdate(volume: Int) {
        _volumeUpdate.postValue(VolumeUpdate(volume))
    }
}