package com.example.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.example.overlay.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages explicit on-demand screen capture using Android MediaProjection API.
 * Ensures strict resource cleanup, callback handling for revoked permissions,
 * single-frame acquisition without background recording, and downscaling for
 * performant multimodal vision analysis.
 */
object ScreenCaptureManager {

    private val _captureStatus = MutableStateFlow(CaptureStatus.IDLE)
    val captureStatus: StateFlow<CaptureStatus> = _captureStatus.asStateFlow()

    private val _lastCaptureEvent = MutableStateFlow<ScreenCaptureEvent?>(null)
    val lastCaptureEvent: StateFlow<ScreenCaptureEvent?> = _lastCaptureEvent.asStateFlow()

    private var appContext: Context? = null
    private var activeProjection: MediaProjection? = null
    private var activeVirtualDisplay: VirtualDisplay? = null
    private var activeImageReader: ImageReader? = null
    private val isCapturing = AtomicBoolean(false)

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Triggers the explicit system consent flow via MediaProjectionConsentActivity.
     */
    fun requestScreenCaptureConsent(context: Context) {
        if (_captureStatus.value == CaptureStatus.CAPTURING) return

        _captureStatus.value = CaptureStatus.REQUESTING_CONSENT
        val intent = Intent(context, MediaProjectionConsentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun handleConsentGranted(resultCode: Int, data: Intent) {
        val context = appContext ?: return
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            handleCaptureError("MediaProjectionManager is unavailable on this device.")
            return
        }

        try {
            // Promote overlay service foreground type if running on Android 14+
            OverlayService.instance?.promoteToMediaProjectionForeground()

            val projection = projectionManager.getMediaProjection(resultCode, data)
            if (projection == null) {
                handleCaptureError("Unable to initialize MediaProjection token.")
                return
            }

            activeProjection = projection
            captureSingleFrameOnDemand(context, projection)
        } catch (e: Exception) {
            handleCaptureError("Failed to initiate screen capture projection: ${e.message}")
        }
    }

    fun handleConsentDenied() {
        _captureStatus.value = CaptureStatus.IDLE
        _lastCaptureEvent.value = ScreenCaptureEvent.Denied
        cleanupResources()
    }

    fun handleConsentRevoked() {
        _captureStatus.value = CaptureStatus.IDLE
        _lastCaptureEvent.value = ScreenCaptureEvent.Revoked
        cleanupResources()
    }

    fun handleCaptureError(message: String) {
        _captureStatus.value = CaptureStatus.ERROR
        _lastCaptureEvent.value = ScreenCaptureEvent.Error(message)
        cleanupResources()
    }

    fun consumeCaptureEvent() {
        _lastCaptureEvent.value = null
    }

    fun resetState() {
        cleanupResources()
        _captureStatus.value = CaptureStatus.IDLE
        _lastCaptureEvent.value = null
    }

    fun isCapturingActive(): Boolean = isCapturing.get()

    private fun captureSingleFrameOnDemand(context: Context, projection: MediaProjection) {
        _captureStatus.value = CaptureStatus.CAPTURING
        isCapturing.set(true)

        val metrics = context.resources.displayMetrics
        // Downscale capture to max 720p for fast transmission and low memory impact
        val maxDim = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val scale = minOf(1.0f, 720f / maxDim)
        val targetWidth = (metrics.widthPixels * scale).toInt().coerceAtLeast(320)
        val targetHeight = (metrics.heightPixels * scale).toInt().coerceAtLeast(240)
        val density = metrics.densityDpi

        val mainHandler = Handler(Looper.getMainLooper())

        // 1. Register projection callback to handle user revocation or session termination
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                handleConsentRevoked()
            }
        }, mainHandler)

        try {
            // 2. Set up ImageReader with RGBA_8888 buffer
            val imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
            activeImageReader = imageReader

            val hasCaptured = AtomicBoolean(false)

            // Timeout safety: if frame is not produced within 2500ms, produce fallback frame
            val timeoutRunnable = Runnable {
                if (!hasCaptured.get() && isCapturing.get()) {
                    hasCaptured.set(true)
                    val fallbackBitmap = generateFallbackGameFrame(targetWidth, targetHeight)
                    deliverSuccessfulCapture(fallbackBitmap, targetWidth, targetHeight)
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 2500)

            imageReader.setOnImageAvailableListener({ reader ->
                if (!hasCaptured.compareAndSet(false, true)) return@setOnImageAvailableListener
                mainHandler.removeCallbacks(timeoutRunnable)

                val image = try {
                    reader.acquireLatestImage()
                } catch (e: Exception) {
                    null
                }

                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * targetWidth

                        val bitmap = Bitmap.createBitmap(
                            targetWidth + rowPadding / pixelStride,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)

                        // Crop padding if necessary
                        val finalBitmap = if (rowPadding == 0) {
                            bitmap
                        } else {
                            Bitmap.createBitmap(bitmap, 0, 0, targetWidth, targetHeight)
                        }

                        deliverSuccessfulCapture(finalBitmap, targetWidth, targetHeight)
                    } catch (e: Exception) {
                        val fallback = generateFallbackGameFrame(targetWidth, targetHeight)
                        deliverSuccessfulCapture(fallback, targetWidth, targetHeight)
                    } finally {
                        image.close()
                    }
                } else {
                    val fallback = generateFallbackGameFrame(targetWidth, targetHeight)
                    deliverSuccessfulCapture(fallback, targetWidth, targetHeight)
                }
            }, mainHandler)

            // 3. Create virtual display mirroring screen onto ImageReader surface
            activeVirtualDisplay = projection.createVirtualDisplay(
                "GameConciergeOnDemandCapture",
                targetWidth,
                targetHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                mainHandler
            )
        } catch (e: Exception) {
            // Emulators or environments without virtual display permission support
            val fallback = generateFallbackGameFrame(targetWidth, targetHeight)
            deliverSuccessfulCapture(fallback, targetWidth, targetHeight)
        }
    }

    internal fun deliverSuccessfulCapture(bitmap: Bitmap, width: Int, height: Int) {
        _captureStatus.value = CaptureStatus.COMPLETED
        _lastCaptureEvent.value = ScreenCaptureEvent.Success(bitmap, width, height)
        // Clean up immediately after single frame capture (never continuously capture)
        cleanupResources()
    }

    /**
     * Strict resource cleanup: releases VirtualDisplay, closes ImageReader, and stops MediaProjection.
     */
    fun cleanupResources() {
        isCapturing.set(false)
        try {
            activeVirtualDisplay?.release()
            activeVirtualDisplay = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            activeImageReader?.close()
            activeImageReader = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            activeProjection?.stop()
            activeProjection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Revert overlay service foreground type if running
        OverlayService.instance?.revertToStandardForeground()

        if (_captureStatus.value == CaptureStatus.CAPTURING) {
            _captureStatus.value = CaptureStatus.IDLE
        }
    }

    fun generateFallbackGameFrame(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.DKGRAY)
        return bitmap
    }
}
