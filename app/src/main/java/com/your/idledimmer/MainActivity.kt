package com.your.idledimmer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This checks if the app is allowed to draw a black layer over other apps
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                Uri.parse("package:$packageName")
            )
            // This opens the system settings page for the user
            startActivityForResult(intent, 1234)
        } else {
            // If we already have permission, start the service immediately
            startDimmerService()
        }
    }

    private fun startDimmerService() {
        val serviceIntent = Intent(this, DimmerService::class.java)
        startForegroundService(serviceIntent)
        
        // Optional: Close the activity so the app doesn't stay on screen
        finish() 
    }

    // This handles the result after the user comes back from the settings page
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234) {
            if (Settings.canDrawOverlays(this)) {
                startDimmerService()
            }
        }
    }
}

