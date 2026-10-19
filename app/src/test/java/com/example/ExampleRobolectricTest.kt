package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.OverlayStateTransition
import com.example.domain.OverlayUiState
import com.example.overlay.OverlayStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches Game Concierge`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val tileName = context.getString(R.string.tile_name)

        assertEquals("Game Concierge", appName)
        assertEquals("Concierge", tileName)
    }

    @Test
    fun `overlay state transition enforces state machine invariants`() {
        // Legal transitions
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.COLLAPSED, OverlayUiState.EXPANDED))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.EXPANDED, OverlayUiState.COLLAPSED))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.EXPANDED, OverlayUiState.LISTENING))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.EXPANDED, OverlayUiState.SPEAKING))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.EXPANDED, OverlayUiState.CAPTURING))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.LISTENING, OverlayUiState.THINKING))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.THINKING, OverlayUiState.SPEAKING))
        assertTrue(OverlayStateTransition.isValid(OverlayUiState.COLLAPSED, OverlayUiState.SUSPENDED))

        // Illegal transitions
        assertFalse(OverlayStateTransition.isValid(OverlayUiState.COLLAPSED, OverlayUiState.LISTENING))
        assertFalse(OverlayStateTransition.isValid(OverlayUiState.COLLAPSED, OverlayUiState.SPEAKING))
        assertFalse(OverlayStateTransition.isValid(OverlayUiState.COLLAPSED, OverlayUiState.CAPTURING))
        assertFalse(OverlayStateTransition.isValid(OverlayUiState.SUSPENDED, OverlayUiState.LISTENING))
    }

    @Test
    fun `overlay state manager updates and clamps position correctly`() {
        OverlayStateManager.updatePosition(isAttachedToRight = true, normalizedY = 0.5f)
        val pos = OverlayStateManager.capsulePosition.value
        assertTrue(pos.isAttachedToRight)
        assertEquals(0.5f, pos.normalizedY, 0.001f)

        // Test clamping to prevent off-screen rendering
        OverlayStateManager.updatePosition(isAttachedToRight = false, normalizedY = -0.5f)
        val clampedTop = OverlayStateManager.capsulePosition.value
        assertFalse(clampedTop.isAttachedToRight)
        assertEquals(0.05f, clampedTop.normalizedY, 0.001f)

        OverlayStateManager.updatePosition(isAttachedToRight = true, normalizedY = 1.5f)
        val clampedBottom = OverlayStateManager.capsulePosition.value
        assertEquals(0.90f, clampedBottom.normalizedY, 0.001f)
    }

    @Test
    fun `service running toggle transitions state cleanly`() {
        OverlayStateManager.setServiceRunning(false)
        assertEquals(OverlayUiState.SUSPENDED, OverlayStateManager.uiState.value)
        assertFalse(OverlayStateManager.isServiceRunning.value)

        OverlayStateManager.setServiceRunning(true)
        assertEquals(OverlayUiState.COLLAPSED, OverlayStateManager.uiState.value)
        assertTrue(OverlayStateManager.isServiceRunning.value)

        // Expand
        val transitioned = OverlayStateManager.requestTransition(OverlayUiState.EXPANDED)
        assertTrue(transitioned)
        assertEquals(OverlayUiState.EXPANDED, OverlayStateManager.uiState.value)

        // Stop
        OverlayStateManager.setServiceRunning(false)
        assertEquals(OverlayUiState.SUSPENDED, OverlayStateManager.uiState.value)
    }
}
