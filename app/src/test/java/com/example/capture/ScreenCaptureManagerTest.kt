package com.example.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenCaptureManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScreenCaptureManager.initialize(context)
        ScreenCaptureManager.resetState()
    }

    @After
    fun tearDown() {
        ScreenCaptureManager.cleanupResources()
        ScreenCaptureManager.resetState()
    }

    @Test
    fun `initial state is IDLE with no active capture and null event`() {
        assertEquals(CaptureStatus.IDLE, ScreenCaptureManager.captureStatus.value)
        assertNull(ScreenCaptureManager.lastCaptureEvent.value)
        assertFalse(ScreenCaptureManager.isCapturingActive())
    }

    @Test
    fun `requestScreenCaptureConsent transitions status to REQUESTING_CONSENT`() {
        ScreenCaptureManager.requestScreenCaptureConsent(context)
        assertEquals(CaptureStatus.REQUESTING_CONSENT, ScreenCaptureManager.captureStatus.value)
    }

    @Test
    fun `handleConsentDenied sets status to IDLE, emits Denied event and cleans up`() {
        ScreenCaptureManager.requestScreenCaptureConsent(context)
        assertEquals(CaptureStatus.REQUESTING_CONSENT, ScreenCaptureManager.captureStatus.value)

        ScreenCaptureManager.handleConsentDenied()

        assertEquals(CaptureStatus.IDLE, ScreenCaptureManager.captureStatus.value)
        assertEquals(ScreenCaptureEvent.Denied, ScreenCaptureManager.lastCaptureEvent.value)
        assertFalse(ScreenCaptureManager.isCapturingActive())
    }

    @Test
    fun `handleConsentRevoked sets status to IDLE, emits Revoked event and releases resources`() {
        ScreenCaptureManager.handleConsentRevoked()

        assertEquals(CaptureStatus.IDLE, ScreenCaptureManager.captureStatus.value)
        assertEquals(ScreenCaptureEvent.Revoked, ScreenCaptureManager.lastCaptureEvent.value)
        assertFalse(ScreenCaptureManager.isCapturingActive())
    }

    @Test
    fun `handleCaptureError sets status to ERROR, emits Error event with detail and cleans up`() {
        val errorMessage = "Hardware virtual display allocation failed"
        ScreenCaptureManager.handleCaptureError(errorMessage)

        assertEquals(CaptureStatus.ERROR, ScreenCaptureManager.captureStatus.value)
        val event = ScreenCaptureManager.lastCaptureEvent.value
        assertTrue(event is ScreenCaptureEvent.Error)
        assertEquals(errorMessage, (event as ScreenCaptureEvent.Error).message)
        assertFalse(ScreenCaptureManager.isCapturingActive())
    }

    @Test
    fun `deliverSuccessfulCapture emits Success event with bitmap dimensions and cleans up`() {
        val testBitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)

        ScreenCaptureManager.deliverSuccessfulCapture(testBitmap, 640, 360)

        assertEquals(CaptureStatus.COMPLETED, ScreenCaptureManager.captureStatus.value)
        val event = ScreenCaptureManager.lastCaptureEvent.value
        assertTrue(event is ScreenCaptureEvent.Success)
        val success = event as ScreenCaptureEvent.Success
        assertEquals(640, success.width)
        assertEquals(360, success.height)
        assertNotNull(success.bitmap)
        assertFalse(ScreenCaptureManager.isCapturingActive())
    }

    @Test
    fun `consumeCaptureEvent resets lastCaptureEvent to null`() {
        ScreenCaptureManager.handleConsentDenied()
        assertEquals(ScreenCaptureEvent.Denied, ScreenCaptureManager.lastCaptureEvent.value)

        ScreenCaptureManager.consumeCaptureEvent()
        assertNull(ScreenCaptureManager.lastCaptureEvent.value)
    }

    @Test
    fun `generateFallbackGameFrame produces valid non-empty bitmap`() {
        val fallback = ScreenCaptureManager.generateFallbackGameFrame(400, 300)
        assertNotNull(fallback)
        assertEquals(400, fallback.width)
        assertEquals(300, fallback.height)
    }
}
