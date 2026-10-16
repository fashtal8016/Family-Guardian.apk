package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.data.repository.FamilyRepository
import com.example.location.LocationHelper
import com.example.notification.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FamilyLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var repository: FamilyRepository

    companion object {
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_PARENT_ID = "extra_parent_id"
        const val EXTRA_CHILD_ID = "extra_child_id"
        var isServiceRunning = false
            private set

        fun start(context: Context, linkId: String, parentId: String, childId: String) {
            val intent = Intent(context, FamilyLocationService::class.java).apply {
                putExtra(EXTRA_LINK_ID, linkId)
                putExtra(EXTRA_PARENT_ID, parentId)
                putExtra(EXTRA_CHILD_ID, childId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FamilyLocationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        locationHelper = LocationHelper(this)
        repository = FamilyRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.buildForegroundLocationNotification()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            notification,
            foregroundServiceType
        )
        isServiceRunning = true

        val linkId = intent?.getStringExtra(EXTRA_LINK_ID) ?: ""
        val parentId = intent?.getStringExtra(EXTRA_PARENT_ID) ?: ""
        val childId = intent?.getStringExtra(EXTRA_CHILD_ID) ?: ""

        if (linkId.isNotEmpty() && parentId.isNotEmpty() && childId.isNotEmpty()) {
            startTrackingLoop(linkId, parentId, childId)
        }

        return START_STICKY
    }

    private fun startTrackingLoop(linkId: String, parentId: String, childId: String) {
        serviceScope.launch {
            // Observe incoming location requests from parent in real-time
            launch {
                try {
                    repository.observePendingRequestsForChild(childId).collect { requests ->
                        for (req in requests) {
                            notificationHelper.showLocationRequestNotification(note = req.message)
                            val loc = locationHelper.getCurrentLocation()
                            if (loc != null) {
                                repository.reportLocation(
                                    parentId = parentId,
                                    childId = childId,
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    accuracy = loc.accuracy.toDouble(),
                                    provider = loc.provider ?: "fused",
                                    speed = loc.speed.toDouble()
                                )
                                repository.updateLocationRequestStatus(req.id, "fulfilled")
                            } else {
                                val status = if (!locationHelper.isGpsEnabled()) "gps_disabled" else "permission_denied"
                                repository.updateLocationRequestStatus(req.id, status)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Periodic heartbeat & telemetry updates every 30 seconds
            while (isActive) {
                try {
                    val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                    val permStatus = when {
                        locationHelper.hasFineLocationPermission() -> "granted"
                        locationHelper.hasLocationPermission() -> "foreground_only"
                        else -> "denied"
                    }
                    val gps = locationHelper.isGpsEnabled()

                    repository.updateChildTelemetry(
                        linkId = linkId,
                        batteryLevel = battery,
                        isOnline = true,
                        isSharing = true,
                        permStatus = permStatus,
                        gpsEnabled = gps
                    )

                    // Also broadcast latest location if available
                    if (locationHelper.hasLocationPermission() && gps) {
                        val loc = locationHelper.getCurrentLocation()
                        if (loc != null) {
                            repository.reportLocation(
                                parentId = parentId,
                                childId = childId,
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                accuracy = loc.accuracy.toDouble(),
                                provider = loc.provider ?: "fused",
                                speed = loc.speed.toDouble()
                            )
                        }
                    }
                } catch (_: Exception) {}

                delay(30_000L)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
