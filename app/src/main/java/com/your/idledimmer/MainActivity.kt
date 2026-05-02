package com.your.idledimmer
import android.content.Intent; import android.net.Uri; import android.os.Bundle; import android.provider.Settings
import android.widget.Button; import androidx.appcompat.app.AppCompatActivity
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else if (!Settings.System.canWrite(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            startForegroundService(Intent(this, DimmerService::class.java))
        }
        findViewById<Button>(R.id.btnLogs).setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
    }
}
