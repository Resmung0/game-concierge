package com.example.voice

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent trampoline activity used exclusively to present the official Android system
 * runtime permission dialog for RECORD_AUDIO when requested from background/overlay services.
 */
class VoicePermissionActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val isPermanent = !isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        VoiceManager.handlePermissionResult(isGranted, isPermanent)
        finishWithNoAnimation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } catch (e: Exception) {
            VoiceManager.handlePermissionResult(isGranted = false, isPermanent = false)
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
