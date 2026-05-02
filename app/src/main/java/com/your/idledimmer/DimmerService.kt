package com.your.idledimmer
import android.app.*; import android.content.*; import android.graphics.*; import android.os.*
import android.provider.Settings; import android.view.*; import kotlinx.coroutines.*
import java.io.*; import java.util.*

class DimmerService : Service() {
    private var originalBrightness = -1
    private var isDimmed = false
    private var overlay: View? = null
    private val threshold = 60 * 1000L 

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chan = NotificationChannel("dim", "Dimmer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        startForeground(1, Notification.Builder(this, "dim").setContentTitle("Dimmer Active").setSmallIcon(android.R.drawable.ic_lock_idle_low_battery).build())
        
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val last = getLastActivity()
                val uptime = SystemClock.uptimeMillis()
                val idle = uptime - last
                log("U:$uptime L:$last I:${idle/1000}s")
                
                if (idle > threshold && !isDimmed) dim(true) 
                else if (idle < threshold && isDimmed) dim(false)
                delay(3000)
            }
        }
        return START_STICKY
    }

    private fun dim(doDim: Boolean) {
        isDimmed = doDim
        Handler(Looper.getMainLooper()).post {
            if (doDim) {
                log("ACTION: DIMMING")
                if (Settings.System.canWrite(this)) {
                    originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 1)
                } else { showOverlay(true) }
            } else {
                log("ACTION: RESET")
                if (originalBrightness != -1) Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, originalBrightness)
                showOverlay(false)
            }
        }
    }

    private fun showOverlay(show: Boolean) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (show && overlay == null) {
            overlay = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0.7f }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            wm.addView(overlay, params)
        } else if (!show && overlay != null) { try { wm.removeView(overlay) } catch(e:Exception) {}; overlay = null }
    }

    private fun getDump(serviceName: String): String {
        return try {
            val sm = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, serviceName) as IBinder
            val pipe = ParcelFileDescriptor.createPipe()
            sm.dump(pipe[1].fileDescriptor, null)
            pipe[1].close()
            val text = BufferedReader(InputStreamReader(FileInputStream(pipe[0].fileDescriptor))).readText()
            pipe[0].close()
            text
        } catch (e: Exception) { "" }
    }

    private fun getLastActivity(): Long {
        val now = SystemClock.uptimeMillis()
        // Try Power Manager first
        val pDump = getDump("power")
        val pTime = pDump.lines().find { it.contains("mLastUserActivityTime", true) }
            ?.substringAfter("=")?.trim()?.split(" ")?.get(0)?.toLongOrNull()
        
        if (pTime != null && pTime > 0) return pTime

        // Fallback: Try Input Dispatcher (Last Event Time)
        val iDump = getDump("input")
        val iTime = iDump.lines().find { it.contains("LastTouchTime", true) || it.contains("LastEventTime", true) }
            ?.substringAfter(":")?.trim()?.split(" ")?.get(0)?.toLongOrNull()

        return iTime ?: now
    }

    private fun log(m: String) { sendBroadcast(Intent("IDLE_LOG").apply { putExtra("log", m) }) }
    override fun onBind(intent: Intent?) = null
}
