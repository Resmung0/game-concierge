package com.example.capture

import android.graphics.Bitmap
import kotlinx.coroutines.delay

interface VisionAnalysisRepository {
    suspend fun analyzeGameScreenshot(
        bitmap: Bitmap,
        activeGame: String,
        simulatedDelayMs: Long = 250L
    ): VisionAnalysisResult
}

class MockVisionAnalysisRepository : VisionAnalysisRepository {

    override suspend fun analyzeGameScreenshot(
        bitmap: Bitmap,
        activeGame: String,
        simulatedDelayMs: Long
    ): VisionAnalysisResult {
        delay(simulatedDelayMs)

        return when {
            activeGame.contains("elden", ignoreCase = true) -> {
                VisionAnalysisResult(
                    detectedScene = "Boss Arena: Margit the Fell Omen (Castleward Tunnel)",
                    visualObservations = listOf(
                        "Enemy health gauge reduced to approximately 30%",
                        "Glowing holy hammer summoned in boss's left hand (Phase 2 active)",
                        "Player HP at 65%, Stamina gauge regenerating",
                        "Flask of Crimson Tears: 2 charges remaining"
                    ),
                    hypothesizedAction = "Boss is initiating jumping hammer leap. Roll directly underneath him upon descent and punish with heavy attack.",
                    confidenceScore = 0.89f,
                    rawAnalysisText = "Visual analysis identifies Margit in Phase 2 with holy hammer drawn. Player has sufficient health to trade or evade forward."
                )
            }
            activeGame.contains("hollow", ignoreCase = true) -> {
                VisionAnalysisResult(
                    detectedScene = "Combat Arena: Mantis Lords (Mantis Village)",
                    visualObservations = listOf(
                        "Dual Mantis Lords active on opposite wall pedestals",
                        "High boomerang trajectory curving downward through arena center",
                        "Player masks at 3/5, Soul vessel half full",
                        "Shade Cloak cooldown ready"
                    ),
                    hypothesizedAction = "Stand stationary in exact center of arena beneath the intersection point of boomerangs to safely heal 1 mask.",
                    confidenceScore = 0.92f,
                    rawAnalysisText = "Visual scan detects Mantis Lords dual boomerang arc. Center of arena safe zone verified."
                )
            }
            activeGame.contains("zelda", ignoreCase = true) || activeGame.contains("totk", ignoreCase = true) -> {
                VisionAnalysisResult(
                    detectedScene = "Aerial Sky Battle: Colgera (Wind Temple)",
                    visualObservations = listOf(
                        "Colgera diving through high-altitude blizzard updraft",
                        "Vulnerable underbelly ice segment exposed during banking turn",
                        "Paraglider deployed, Battery cells at 3/3",
                        "Inventory: Keese Eyeballs and Aerocuda Wings available"
                    ),
                    hypothesizedAction = "Drop paraglider into free-fall dive directly through the glowing ice carapace to shatter the segment without consuming arrows.",
                    confidenceScore = 0.86f,
                    rawAnalysisText = "Aerial telemetry confirms Colgera underbelly ice fracture state. Free-fall collision trajectory optimal."
                )
            }
            else -> {
                VisionAnalysisResult(
                    detectedScene = "Active Gameplay Scene ($activeGame)",
                    visualObservations = listOf(
                        "HUD health and resource indicators detected on screen perimeter",
                        "Combat encounter in progress with humanoid adversary",
                        "Defensive evasion window currently open"
                    ),
                    hypothesizedAction = "Reposition away from obstacle corners and activate defensive buff.",
                    confidenceScore = 0.82f,
                    rawAnalysisText = "General HUD analysis complete. Standard evasion and defensive protocol recommended."
                )
            }
        }
    }
}
