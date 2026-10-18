package com.example.capture

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisionAnalysisRepositoryTest {

    private lateinit var repository: MockVisionAnalysisRepository
    private lateinit var dummyBitmap: Bitmap

    @Before
    fun setUp() {
        repository = MockVisionAnalysisRepository()
        dummyBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun `analyzeGameScreenshot for Elden Ring identifies Margit boss arena with high confidence`() = runTest {
        val result = repository.analyzeGameScreenshot(dummyBitmap, "Elden Ring", simulatedDelayMs = 0L)

        assertNotNull(result)
        assertTrue(result.detectedScene.contains("Margit"))
        assertEquals(4, result.visualObservations.size)
        assertTrue(result.visualObservations.any { it.contains("holy hammer") })
        assertTrue(result.hypothesizedAction.contains("jumping hammer leap") || result.hypothesizedAction.contains("Roll"))
        assertTrue(result.confidenceScore >= 0.85f)
        assertTrue(result.rawAnalysisText.isNotEmpty())
    }

    @Test
    fun `analyzeGameScreenshot for Hollow Knight detects Mantis Lords and arena safe zone`() = runTest {
        val result = repository.analyzeGameScreenshot(dummyBitmap, "Hollow Knight", simulatedDelayMs = 0L)

        assertNotNull(result)
        assertTrue(result.detectedScene.contains("Mantis Lords"))
        assertTrue(result.visualObservations.any { it.contains("boomerang") })
        assertTrue(result.hypothesizedAction.contains("center of arena"))
        assertEquals(0.92f, result.confidenceScore, 0.01f)
    }

    @Test
    fun `analyzeGameScreenshot for Zelda TotK detects Colgera and free fall dive trajectory`() = runTest {
        val result = repository.analyzeGameScreenshot(dummyBitmap, "Zelda: TotK", simulatedDelayMs = 0L)

        assertNotNull(result)
        assertTrue(result.detectedScene.contains("Colgera"))
        assertTrue(result.visualObservations.any { it.contains("ice") })
        assertTrue(result.hypothesizedAction.contains("free-fall") || result.hypothesizedAction.contains("dive"))
    }

    @Test
    fun `analyzeGameScreenshot for generic game provides default observations and evasion protocol`() = runTest {
        val result = repository.analyzeGameScreenshot(dummyBitmap, "Unknown Indie Title", simulatedDelayMs = 0L)

        assertNotNull(result)
        assertTrue(result.detectedScene.contains("Unknown Indie Title"))
        assertTrue(result.visualObservations.isNotEmpty())
        assertTrue(result.hypothesizedAction.contains("defensive") || result.hypothesizedAction.contains("evasion"))
    }
}
