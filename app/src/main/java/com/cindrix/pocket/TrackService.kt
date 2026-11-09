package com.cindrix.pocket

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Keeps the GPS running while the screen is off, with a notice in the drawer so the person always knows it is on.
 * Uses the phone's own GPS through LocationManager: no Google Play services, no network, nothing sent anywhere.
 */
class TrackService : Service(), LocationListener {
    private var manager: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TrackRecorder.init(filesDir)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(CHANNEL, "Recording a track", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL)
            .setContentTitle("Cindrix Pocket is recording your track")
            .setContentText("The GPS is on. Open the app to stop.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTICE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTICE_ID, note)
        }
        listen()
        return START_NOT_STICKY
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            TrackRecorder.stop()
            stopSelf()
            return
        }
        val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager = m
        try {
            // a fix at most every 5 seconds or 5 metres: enough for a walk, gentle on the battery
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this, Looper.getMainLooper())
        } catch (e: Exception) {
            TrackRecorder.stop()
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        // a fix worse than 50 metres would draw a zig-zag through the trees, so it is skipped
        if (location.hasAccuracy() && location.accuracy > 50f) return
        TrackRecorder.add(TrackPoint(location.time / 1000, location.latitude, location.longitude, height(location)))
    }

    /** Height above sea level. Before Android 14 the phone gives height above the GPS ellipsoid, which is a constant
     *  amount off for a place (about 30 m in West Virginia), so climbing and dropping are right but the top is rough. */
    private fun height(location: Location): Double? {
        if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) return location.mslAltitudeMeters
        return if (location.hasAltitude()) location.altitude else null
    }

    @Deprecated("Needed on older Android versions")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        try { manager?.removeUpdates(this) } catch (e: Exception) { /* already gone */ }
        TrackRecorder.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "track"
        private const val NOTICE_ID = 7

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackService::class.java))
        }
    }
}
