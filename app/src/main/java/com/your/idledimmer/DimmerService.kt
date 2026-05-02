package com.your.idledimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

class DimmerService : Service() {

    private var pollJob: Job? = null
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager

    // Set your idle timeout here (e.g., 2 minutes)
    private val IDLE_THRESHOLD_MS = 2 * 60 * 1000L 

    // Listens for Screen ON/OFF to save CPU
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> startPollingLoop()
                Intent.ACTION_SCREEN_OFF -> stopPollingLoop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Register the Screen ON/OFF receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Start Foreground Notification to prevent Android 10 from killing it
        createNotificationChannel()
        startForeground(1, Notification.Builder(this, "dimmer_channel")
            .setContentTitle("IdleDimmer Active")
            .setContentText("Monitoring screen idle time...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        )

        // Assume screen is ON when service starts
        startPollingLoop() 
    }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val lastActivityTime = getLastUserActivityTime()
                val uptime = SystemClock.uptimeMillis()

                if (uptime - lastActivityTime > IDLE_THRESHOLD_MS) {
                    withContext(Dispatchers.Main) { showDimmerOverlay() }
                } else {
                    withContext(Dispatchers.Main) { hideDimmerOverlay() }
                }

                // Poll every 5 seconds (Zero CPU cost while delaying)
                delay(5000)
            }
        }
    }

    private fun stopPollingLoop() {
        pollJob?.cancel()
        hideDimmerOverlay() // Ensure overlay is gone when screen turns off
    }

    // ----------------------------------------------------
    // THE MAGIC: Binder Reflection (No Shizuku Needed)
    // ----------------------------------------------------
    private fun getLastUserActivityTime(): Long {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val powerBinder = getServiceMethod.invoke(null, "power") as IBinder

            val pipe = ParcelFileDescriptor.createPipe()
            val readFd = pipe[0]
            val writeFd = pipe[1]

            // Requires DUMP permission!
            powerBinder.dump(writeFd.fileDescriptor, null)
            writeFd.close()

            val reader = BufferedReader(InputStreamReader(FileInputStream(readFd.fileDescriptor)))
            var lastTime: Long? = null
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("mLastUserActivityTime")) {
                    // Extract the number from "  mLastUserActivityTime=12345678"
                    lastTime = line!!.substringAfter("=").trim().toLongOrNull()
                    break
                }
            }
            reader.close()
            readFd.close()

            return lastTime ?: SystemClock.uptimeMillis()

        } catch (e: Exception) {
            e.printStackTrace()
            return SystemClock.uptimeMillis()
        }
    }

    // ----------------------------------------------------
    // UI: The Overlay
    // ----------------------------------------------------
    private fun showDimmerOverlay() {
        if (overlayView != null) return

        overlayView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.85f // 85% black, adjust to your liking
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)
    }

    private fun hideDimmerOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("dimmer_channel", "Dimmer Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        stopPollingLoop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

