package com.example.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.overlay.OverlayService
import com.example.overlay.OverlayStateManager
import com.example.util.PermissionUtils

/**
 * Quick Settings Tile allowing players to toggle Game Concierge directly from the notification shade.
 * Complies with Android 14+ (API 34+) background execution rules and tile activation standards.
 */
class GameConciergeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // 1. Verify overlay permission before attempting companion launch
        if (!PermissionUtils.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                getString(R.string.status_needs_permission),
                Toast.LENGTH_LONG
            ).show()

            // Route to MainActivity onboarding screen
            launchMainActivity()
            return
        }

        // 2. Toggle companion overlay
        val isRunning = OverlayStateManager.isServiceRunning.value
        if (isRunning) {
            // If already running, stop or toggle
            OverlayService.stop(this)
        } else {
            try {
                OverlayService.start(this)
            } catch (e: Exception) {
                // If background launch restrictions prevent start, fallback to trampoline activity
                launchMainActivity()
            }
        }

        updateTileState()
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = OverlayStateManager.isServiceRunning.value

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) {
                getString(R.string.tile_active)
            } else {
                getString(R.string.tile_inactive)
            }
        }

        tile.updateTile()
    }
}
