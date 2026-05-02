package com.your.idledimmer
import android.app.*; import android.content.*; import android.graphics.*; import android.os.*
import android.provider.Settings; import android.view.*; import kotlinx.coroutines.*
import java.io.*; import java.util.*

class DimmerService : Service() {
    private var originalBrightness = -1
    private var isDimmed = false
    private var overlay: View? = null
    private val threshold = 60 * 1000L // 1 minute

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chan = NotificationChannel("dim", "Dimmer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        startForeground(1, Notification.Builder(this, "dim").setContentTitle("Dimmer Active").setSmallIcon(android.R.drawable.ic_lock_idle_low_battery).build())
        
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val idle = SystemClock.uptimeMillis() - getLastActivity()
                log("Idle: ${idle/1000}s")
                if (idle > threshold && !isDimmed) dim(true) else if (idle < threshold && isDimmed) dim(false)
                delay(3000)
            }
        }
        return START_STICKY
    }

    private fun dim(doDim: Boolean) {
        isDimmed = doDim
        Handler(Looper.getMainLooper()).post {
            if (doDim) {
                if (Settings.System.canWrite(this)) {
                    originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 1)
                } else { showOverlay(true) }
            } else {
                if (originalBrightness != -1) Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, originalBrightness)
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
        } else if (!show && overlay != null) { wm.removeView(overlay); overlay = null }
    }

    private fun getLastActivity(): Long {
        return try {
            val sm = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "power") as IBinder
            val pipe = ParcelFileDescriptor.createPipe()
            sm.dump(pipe[1].fileDescriptor, null)
            pipe[1].close()
            val out = BufferedReader(InputStreamReader(FileInputStream(pipe[0].fileDescriptor))).readText()
            out.lines().find { it.contains("mLastUserActivityTime") }?.substringAfter("=")?.trim()?.toLong() ?: SystemClock.uptimeMillis()
        } catch (e: Exception) { SystemClock.uptimeMillis() }
    }

    private fun log(m: String) { sendBroadcast(Intent("IDLE_LOG").apply { putExtra("log", m) }) }
    override fun onBind(intent: Intent?) = null
}
