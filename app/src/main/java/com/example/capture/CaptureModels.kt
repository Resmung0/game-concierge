package com.example.capture

import android.graphics.Bitmap

sealed interface ScreenCaptureEvent {
    data class Success(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : ScreenCaptureEvent

    data object Denied : ScreenCaptureEvent
    data class Error(val message: String) : ScreenCaptureEvent
    data object Revoked : ScreenCaptureEvent
}

enum class CaptureStatus {
    IDLE,
    REQUESTING_CONSENT,
    CAPTURING,
    ANALYZING,
    COMPLETED,
    ERROR
}

data class VisionAnalysisResult(
    val detectedScene: String,
    val visualObservations: List<String>,
    val hypothesizedAction: String,
    val confidenceScore: Float,
    val rawAnalysisText: String
)
