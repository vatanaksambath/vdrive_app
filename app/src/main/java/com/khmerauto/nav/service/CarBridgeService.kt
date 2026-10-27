package com.khmerauto.nav.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.khmerauto.nav.MainActivity
import com.khmerauto.nav.R
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.hal.geely.GeelyFlymeHalAdapter
import com.khmerauto.nav.hal.wifi.CarHalWebSocketServer

private const val TAG = "CarBridgeService"
private const val CHANNEL_ID = "kmautonav_car_bridge"
private const val NOTIFICATION_ID = 1001

/**
 * Foreground Service that keeps the Ktor WebSocket Server alive in the background
 * on the Geely Galaxy M7 head unit so the external Android Tablet maintains continuous
 * control and telemetry streaming even if the driver minimizes KMAutoNav.
 */
class CarBridgeService : Service() {

    private var halAdapter: GeelyFlymeHalAdapter? = null
    private var webSocketServer: CarHalWebSocketServer? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating CarBridgeService on Geely Galaxy M7...")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // Initialize Native Geely Galaxy M7 HAL
        val hal = GeelyFlymeHalAdapter(applicationContext)
        hal.start()
        halAdapter = hal

        // Launch Embedded WebSocket Server
        val server = CarHalWebSocketServer(vehicleHal = hal, port = 8888)
        server.start()
        webSocketServer = server
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "CarBridgeService started.")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "CarBridgeService destroying...")
        webSocketServer?.stop()
        halAdapter?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val localIp = CarHalWebSocketServer.getLocalIpAddress()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VDrive • Geely Galaxy M7 Bridge")
            .setContentText("Hosting Tablet Wi-Fi Remote on ws://$localIp:8888/ws/hal")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CarBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CarBridgeService::class.java)
            context.stopService(intent)
        }
    }
}
