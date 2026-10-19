package com.example.context.data

import com.example.context.model.ConfidenceLevel
import com.example.context.model.GameKnowledgeItem
import com.example.context.model.GameObservation
import com.example.context.model.GameSessionContext
import com.example.context.model.ObservationSource
import com.example.context.model.ObservationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

interface GameContextRepository {
    val sessionContext: StateFlow<GameSessionContext>

    fun selectGame(gameId: String, gameTitle: String)
    fun setLocation(location: String)
    fun setObjective(objective: String)

    fun addVisualObservation(description: String, confidence: ConfidenceLevel = ConfidenceLevel.MEDIUM): GameObservation
    fun addHypothesis(hypothesis: String, requiresConfirmation: Boolean = true): GameObservation

    fun confirmHypothesis(observationId: String, playerCorrection: String? = null)
    fun dismissHypothesis(observationId: String)
    fun addConfirmedFact(fact: String)

    fun clearSessionObservations()
    fun resetFullSession()
}

class DefaultGameContextRepository(
    private val knowledgeRepo: GameKnowledgeRepository = InMemoryGameKnowledgeRepository()
) : GameContextRepository {

    private val _sessionContext = MutableStateFlow(
        createInitialContext("elden_ring", "Elden Ring")
    )
    override val sessionContext: StateFlow<GameSessionContext> = _sessionContext.asStateFlow()

    private fun createInitialContext(gameId: String, gameTitle: String): GameSessionContext {
        val guides = knowledgeRepo.getGuidesForGame(gameId)
        val defaultLocation = when (gameId) {
            "elden_ring" -> "Stormhill / Limgrave"
            "hollow_knight" -> "Dirtmouth & Crossroads"
            "zelda_totk" -> "Great Sky Island"
            "cyberpunk_2077" -> "Watson, Night City"
            else -> "Initial Area"
        }
        val defaultObjective = when (gameId) {
            "elden_ring" -> "Explore Castleward Tunnel & reach Stormveil"
            "hollow_knight" -> "Explore Forgotten Crossroads"
            "zelda_totk" -> "Reach the Temple of Time"
            "cyberpunk_2077" -> "Meet Jackie at Afterlife"
            else -> "Explore and advance"
        }

        return GameSessionContext(
            gameId = gameId,
            gameTitle = gameTitle,
            currentLocation = defaultLocation,
            currentObjective = defaultObjective,
            verifiedKnowledge = guides,
            confirmedFacts = listOf(
                GameObservation(
                    id = UUID.randomUUID().toString(),
                    gameId = gameId,
                    type = ObservationType.PLAYER_CONFIRMED_FACT,
                    content = "Started session in $gameTitle at $defaultLocation.",
                    source = ObservationSource.MANUAL_SELECTION,
                    confidence = ConfidenceLevel.CERTAIN,
                    isPendingConfirmation = false
                )
            ),
            hypotheses = listOf(
                GameObservation(
                    id = UUID.randomUUID().toString(),
                    gameId = gameId,
                    type = ObservationType.AGENT_HYPOTHESIS,
                    content = "Current challenge appears to be early progression near $defaultLocation.",
                    source = ObservationSource.MANUAL_SELECTION,
                    confidence = ConfidenceLevel.HYPOTHESIS,
                    isPendingConfirmation = true
                )
            ),
            pendingConfirmations = emptyList()
        ).let { initContext ->
            initContext.copy(pendingConfirmations = initContext.hypotheses.filter { it.isPendingConfirmation })
        }
    }

    override fun selectGame(gameId: String, gameTitle: String) {
        _sessionContext.value = createInitialContext(gameId, gameTitle)
    }

    override fun setLocation(location: String) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return
        _sessionContext.update {
            it.copy(
                currentLocation = trimmed,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    override fun setObjective(objective: String) {
        val trimmed = objective.trim()
        if (trimmed.isEmpty()) return
        _sessionContext.update {
            it.copy(
                currentObjective = trimmed,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    override fun addVisualObservation(description: String, confidence: ConfidenceLevel): GameObservation {
        val observation = GameObservation(
            id = UUID.randomUUID().toString(),
            gameId = _sessionContext.value.gameId,
            type = ObservationType.VISUAL_OBSERVATION,
            content = description,
            source = ObservationSource.SCREENSHOT_ANALYSIS,
            confidence = confidence,
            isPendingConfirmation = false
        )

        _sessionContext.update { state ->
            state.copy(
                visualObservations = state.visualObservations + observation,
                lastUpdated = System.currentTimeMillis()
            )
        }
        return observation
    }

    override fun addHypothesis(hypothesis: String, requiresConfirmation: Boolean): GameObservation {
        val observation = GameObservation(
            id = UUID.randomUUID().toString(),
            gameId = _sessionContext.value.gameId,
            type = ObservationType.AGENT_HYPOTHESIS,
            content = hypothesis,
            source = ObservationSource.MANUAL_SELECTION,
            confidence = ConfidenceLevel.HYPOTHESIS,
            isPendingConfirmation = requiresConfirmation
        )

        _sessionContext.update { state ->
            val newHypotheses = state.hypotheses + observation
            val newPending = if (requiresConfirmation) {
                state.pendingConfirmations + observation
            } else {
                state.pendingConfirmations
            }
            state.copy(
                hypotheses = newHypotheses,
                pendingConfirmations = newPending,
                lastUpdated = System.currentTimeMillis()
            )
        }
        return observation
    }

    /**
     * Promotes an observation or hypothesis to a PLAYER_CONFIRMED_FACT upon explicit player confirmation.
     * Incorporates player correction if provided.
     */
    override fun confirmHypothesis(observationId: String, playerCorrection: String?) {
        _sessionContext.update { state ->
            val target = state.hypotheses.find { it.id == observationId }
                ?: state.pendingConfirmations.find { it.id == observationId }
                ?: state.visualObservations.find { it.id == observationId }

            if (target != null) {
                val confirmedFact = target.copy(
                    id = UUID.randomUUID().toString(),
                    type = ObservationType.PLAYER_CONFIRMED_FACT,
                    content = playerCorrection ?: target.content,
                    source = ObservationSource.PLAYER_CONFIRMATION,
                    confidence = ConfidenceLevel.CERTAIN,
                    isPendingConfirmation = false,
                    timestamp = System.currentTimeMillis()
                )

                state.copy(
                    confirmedFacts = state.confirmedFacts + confirmedFact,
                    hypotheses = state.hypotheses.filterNot { it.id == observationId },
                    pendingConfirmations = state.pendingConfirmations.filterNot { it.id == observationId },
                    visualObservations = state.visualObservations.filterNot { it.id == observationId },
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                state
            }
        }
    }

    override fun dismissHypothesis(observationId: String) {
        _sessionContext.update { state ->
            state.copy(
                hypotheses = state.hypotheses.filterNot { it.id == observationId },
                pendingConfirmations = state.pendingConfirmations.filterNot { it.id == observationId },
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    override fun addConfirmedFact(fact: String) {
        val trimmed = fact.trim()
        if (trimmed.isEmpty()) return
        val confirmed = GameObservation(
            id = UUID.randomUUID().toString(),
            gameId = _sessionContext.value.gameId,
            type = ObservationType.PLAYER_CONFIRMED_FACT,
            content = trimmed,
            source = ObservationSource.PLAYER_CONFIRMATION,
            confidence = ConfidenceLevel.CERTAIN,
            isPendingConfirmation = false
        )
        _sessionContext.update { state ->
            state.copy(
                confirmedFacts = state.confirmedFacts + confirmed,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    override fun clearSessionObservations() {
        _sessionContext.update { state ->
            state.copy(
                visualObservations = emptyList(),
                hypotheses = emptyList(),
                pendingConfirmations = emptyList(),
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    override fun resetFullSession() {
        val currentGameId = _sessionContext.value.gameId
        val currentGameTitle = _sessionContext.value.gameTitle
        _sessionContext.value = createInitialContext(currentGameId, currentGameTitle)
    }
}
