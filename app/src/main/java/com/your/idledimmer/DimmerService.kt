package com.your.idledimmer
import android.app.*; import android.content.*; import android.graphics.*; import android.os.*
import android.provider.Settings; import android.view.*; import kotlinx.coroutines.*
import java.io.*; import java.util.*

class DimmerService : Service() {
    private var isDimmed = false
    private var overlay: View? = null
    private var lastRecordedTouch = 0L
    private var idleSeconds = 0L
    private val threshold = 60 // 60 seconds

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chan = NotificationChannel("dim", "Dimmer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        startForeground(1, Notification.Builder(this, "dim").setContentTitle("IdleDimmer").setSmallIcon(android.R.drawable.ic_lock_idle_low_battery).build())
        
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val currentTouch = getRawInputTouchTime()
                
                if (currentTouch != lastRecordedTouch) {
                    // TOUCH DETECTED: Reset everything
                    lastRecordedTouch = currentTouch
                    idleSeconds = 0
                    if (isDimmed) dim(false)
                    log("Touch Detected: Resetting")
                } else {
                    // NO TOUCH: Increase idle count
                    idleSeconds += 2
                    log("Idle: ${idleSeconds}s")
                }

                if (idleSeconds >= threshold && !isDimmed) dim(true)
                delay(2000)
            }
        }
        return START_STICKY
    }

    private fun getRawInputTouchTime(): Long {
        return try {
            val sm = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "input") as IBinder
            val pipe = ParcelFileDescriptor.createPipe()
            sm.dump(pipe[1].fileDescriptor, null)
            pipe[1].close()
            val out = BufferedReader(InputStreamReader(FileInputStream(pipe[0].fileDescriptor))).readText()
            pipe[0].close()
            
            // We look for the Input Dispatcher's last event time
            // ColorOS/Oppo usually labels this as 'LastEventTime' or 'lastTimestamp'
            val line = out.lines().find { it.contains("LastEventTime", true) || it.contains("lastTimestamp", true) }
            line?.substringAfter(":")?.trim()?.split(" ")?.get(0)?.toLong() ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun dim(doDim: Boolean) {
        isDimmed = doDim
        Handler(Looper.getMainLooper()).post {
            if (doDim) {
                if (Settings.System.canWrite(this)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 1)
                }
                showOverlay(true)
            } else {
                if (Settings.System.canWrite(this)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 150)
                }
                showOverlay(false)
            }
        }
    }

    private fun showOverlay(show: Boolean) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (show && overlay == null) {
            overlay = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0.7f }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            wm.addView(overlay, params)
        } else if (!show && overlay != null) {
            try { wm.removeView(overlay) } catch(e:Exception) {}
            overlay = null
        }
    }

    private fun log(m: String) { sendBroadcast(Intent("IDLE_LOG").apply { putExtra("log", m) }) }
    override fun onBind(intent: Intent?) = null
}
