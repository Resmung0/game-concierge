package com.example.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent trampoline activity used exclusively to present the official Android system
 * screen capture consent prompt to the user and relay the result back to ScreenCaptureManager.
 */
class MediaProjectionConsentActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureManager.handleConsentGranted(result.resultCode, result.data!!)
        } else {
            ScreenCaptureManager.handleConsentDenied()
        }
        finishWithNoAnimation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mediaProjectionManager == null) {
            ScreenCaptureManager.handleCaptureError("MediaProjectionManager is unavailable on this device.")
            finishWithNoAnimation()
            return
        }

        try {
            captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            ScreenCaptureManager.handleCaptureError("Failed to launch system screen capture dialog: ${e.message}")
            finishWithNoAnimation()
        }
    }

    private fun finishWithNoAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
