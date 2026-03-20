/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import org.lunaris.dolby.data.DolbyRepository

class DolbyEffectService : Service() {

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val handler = Handler()
    private lateinit var repository: DolbyRepository

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            repository.updateSpeakerState()
            repository.applySavedState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            repository.updateSpeakerState()
            repository.applySavedState()
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val isActive = configs?.any { it.isActive } == true
            if (isActive) {
                repository.applySavedState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // If running on O or later, promote service to foreground to avoid background-start restrictions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = NOTIFICATION_CHANNEL_ID
                if (nm.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(channelId, "Dolby Service", NotificationManager.IMPORTANCE_LOW)
                    nm.createNotificationChannel(channel)
                }
                val notif = Notification.Builder(this, channelId)
                    .setContentTitle("Dolby")
                    .setContentText("Dolby audio service running")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                startForeground(NOTIFICATION_ID, notif)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start foreground notification: ${e.message}")
            }
        }

        repository = DolbyRepository(this)
        repository.applySavedState()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        Log.d(TAG, "Dolby effect service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        repository.applySavedState()
        return START_STICKY
    }

    override fun onDestroy() {
        // Stop foreground state first
        try { stopForeground(true) } catch (_: Exception) {}
        super.onDestroy()
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Dolby effect service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DolbyEffectService"
        private const val NOTIFICATION_ID = 1187
        private const val NOTIFICATION_CHANNEL_ID = "dolby_service"

        fun start(context: Context) {
            val intent = Intent(context, DolbyEffectService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DolbyEffectService::class.java)
            context.stopService(intent)
        }
    }
}
