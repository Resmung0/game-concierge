package com.example.context.model

enum class ObservationSource {
    MANUAL_SELECTION,
    SYSTEM_FOREGROUND,
    SCREENSHOT_ANALYSIS,
    PLAYER_CONFIRMATION
}

enum class ConfidenceLevel {
    CERTAIN,
    HIGH,
    MEDIUM,
    LOW,
    HYPOTHESIS
}

enum class ObservationType {
    VERIFIED_KNOWLEDGE,       // Curated, authoritative game data (rules, boss attacks, puzzle solutions)
    PLAYER_CONFIRMED_FACT,     // Fact explicitly confirmed or verified by the player
    VISUAL_OBSERVATION,        // Direct observation from screen capture (temporary/unconfirmed)
    AGENT_HYPOTHESIS           // Inferred state or deduction (requires player verification)
}

data class GameObservation(
    val id: String,
    val gameId: String,
    val type: ObservationType,
    val content: String,
    val source: ObservationSource,
    val confidence: ConfidenceLevel,
    val timestamp: Long = System.currentTimeMillis(),
    val isPendingConfirmation: Boolean = false
)

enum class GuideCategory {
    BOSS,
    PUZZLE,
    COLLECTIBLE,
    QUEST,
    BUILD_TACTIC,
    GENERAL
}

data class GameKnowledgeItem(
    val id: String,
    val gameId: String,
    val category: GuideCategory,
    val title: String,
    val locationOrBoss: String,
    val summary: String,
    val tacticalSteps: List<String>,
    val tags: List<String>
)

data class GameSessionContext(
    val gameId: String,
    val gameTitle: String,
    val currentLocation: String = "Unknown Area",
    val currentObjective: String = "Explore & Progress",
    val verifiedKnowledge: List<GameKnowledgeItem> = emptyList(),
    val confirmedFacts: List<GameObservation> = emptyList(),
    val visualObservations: List<GameObservation> = emptyList(),
    val hypotheses: List<GameObservation> = emptyList(),
    val pendingConfirmations: List<GameObservation> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)
