package com.your.idledimmer
import android.content.*; import android.os.Bundle; import android.widget.TextView; import androidx.appcompat.app.AppCompatActivity
class LogActivity : AppCompatActivity() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { findViewById<TextView>(R.id.logOutput).append("\n${i?.getStringExtra("log")}") }
    }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); setContentView(R.layout.activity_logs)
        registerReceiver(receiver, IntentFilter("IDLE_LOG"), RECEIVER_EXPORTED)
    }
    override fun onDestroy() { super.onDestroy(); unregisterReceiver(receiver) }
}
