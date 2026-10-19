package com.example.context

import com.example.context.data.DefaultGameContextRepository
import com.example.context.data.InMemoryGameKnowledgeRepository
import com.example.context.model.ConfidenceLevel
import com.example.context.model.GuideCategory
import com.example.context.model.ObservationSource
import com.example.context.model.ObservationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameContextRepositoryTest {

    private lateinit var knowledgeRepo: InMemoryGameKnowledgeRepository
    private lateinit var contextRepo: DefaultGameContextRepository

    @Before
    fun setUp() {
        knowledgeRepo = InMemoryGameKnowledgeRepository()
        contextRepo = DefaultGameContextRepository(knowledgeRepo)
    }

    @Test
    fun `initial session context loads Elden Ring knowledge and defaults`() {
        val context = contextRepo.sessionContext.value
        assertEquals("elden_ring", context.gameId)
        assertEquals("Elden Ring", context.gameTitle)
        assertTrue(context.currentLocation.contains("Limgrave") || context.currentLocation.contains("Stormhill"))
        assertTrue(context.verifiedKnowledge.isNotEmpty())
        assertTrue(context.confirmedFacts.isNotEmpty())
        assertEquals(ObservationType.PLAYER_CONFIRMED_FACT, context.confirmedFacts.first().type)
    }

    @Test
    fun `selectGame updates game, loads relevant guides and resets context`() {
        contextRepo.selectGame("hollow_knight", "Hollow Knight")

        val context = contextRepo.sessionContext.value
        assertEquals("hollow_knight", context.gameId)
        assertEquals("Hollow Knight", context.gameTitle)
        assertTrue(context.verifiedKnowledge.any { it.title.contains("Mantis Lords") })
        assertTrue(context.verifiedKnowledge.any { it.title.contains("Radiance") })
    }

    @Test
    fun `visual observations remain unconfirmed and separated from confirmed progress`() {
        val obs = contextRepo.addVisualObservation(
            description = "Boss health bar depleted to 15% and entering glowing rage phase",
            confidence = ConfidenceLevel.HIGH
        )

        val context = contextRepo.sessionContext.value
        assertEquals(ObservationType.VISUAL_OBSERVATION, obs.type)
        assertEquals(ObservationSource.SCREENSHOT_ANALYSIS, obs.source)

        // Must be in visualObservations, NOT in confirmedFacts
        assertTrue(context.visualObservations.any { it.id == obs.id })
        assertFalse(context.confirmedFacts.any { it.id == obs.id })
    }

    @Test
    fun `hypothesis creation stays pending until player confirms`() {
        val hypothesis = contextRepo.addHypothesis(
            hypothesis = "Player is attempting to defeat Margit without spirit summons",
            requiresConfirmation = true
        )

        val context = contextRepo.sessionContext.value
        assertEquals(ObservationType.AGENT_HYPOTHESIS, hypothesis.type)
        assertTrue(context.hypotheses.any { it.id == hypothesis.id })
        assertTrue(context.pendingConfirmations.any { it.id == hypothesis.id })
        assertFalse(context.confirmedFacts.any { it.id == hypothesis.id })

        // When player confirms
        contextRepo.confirmHypothesis(hypothesis.id)

        val updatedContext = contextRepo.sessionContext.value
        assertFalse(updatedContext.pendingConfirmations.any { it.id == hypothesis.id })
        assertFalse(updatedContext.hypotheses.any { it.id == hypothesis.id })

        val promotedFact = updatedContext.confirmedFacts.find { it.content == hypothesis.content }
        assertNotNull(promotedFact)
        assertEquals(ObservationType.PLAYER_CONFIRMED_FACT, promotedFact?.type)
        assertEquals(ObservationSource.PLAYER_CONFIRMATION, promotedFact?.source)
        assertEquals(ConfidenceLevel.CERTAIN, promotedFact?.confidence)
    }

    @Test
    fun `confirmHypothesis with player correction stores corrected version as fact`() {
        val hypothesis = contextRepo.addHypothesis(
            hypothesis = "Player is stuck on Phase 1",
            requiresConfirmation = true
        )

        // Player corrects the assistant
        contextRepo.confirmHypothesis(
            observationId = hypothesis.id,
            playerCorrection = "Actually in Phase 2 with hammer attacks active"
        )

        val updatedContext = contextRepo.sessionContext.value
        val fact = updatedContext.confirmedFacts.find { it.content.contains("Phase 2 with hammer") }
        assertNotNull(fact)
        assertEquals(ObservationType.PLAYER_CONFIRMED_FACT, fact?.type)
        assertFalse(updatedContext.confirmedFacts.any { it.content == "Player is stuck on Phase 1" })
    }

    @Test
    fun `dismissHypothesis removes pending hypothesis without adding fact`() {
        val hypothesis = contextRepo.addHypothesis(
            hypothesis = "Player may have missed the flask upgrade",
            requiresConfirmation = true
        )

        contextRepo.dismissHypothesis(hypothesis.id)

        val updatedContext = contextRepo.sessionContext.value
        assertFalse(updatedContext.hypotheses.any { it.id == hypothesis.id })
        assertFalse(updatedContext.pendingConfirmations.any { it.id == hypothesis.id })
        assertFalse(updatedContext.confirmedFacts.any { it.id == hypothesis.id })
    }

    @Test
    fun `knowledge repository queries guide categories and search tokens correctly`() {
        val bossGuides = knowledgeRepo.queryGuides("elden_ring", "Margit", GuideCategory.BOSS)
        assertEquals(1, bossGuides.size)
        assertEquals("Margit, the Fell Omen", bossGuides.first().title)

        val puzzleGuides = knowledgeRepo.queryGuides("elden_ring", "invisible", GuideCategory.PUZZLE)
        assertEquals(1, puzzleGuides.size)
        assertTrue(puzzleGuides.first().title.contains("Heretical Rise"))

        val totkGuides = knowledgeRepo.queryGuides("zelda_totk", "Colgera")
        assertEquals(1, totkGuides.size)
        assertTrue(totkGuides.first().tacticalSteps.any { it.contains("free fall") || it.contains("diving") })
    }

    @Test
    fun `resetFullSession wipes transient observations and resets facts to initial game state`() {
        contextRepo.addVisualObservation("Transient boss observation", ConfidenceLevel.HIGH)
        contextRepo.addHypothesis("Transient hypothesis", requiresConfirmation = true)
        contextRepo.addConfirmedFact("Player acquired sacred tear")

        val stateBefore = contextRepo.sessionContext.value
        assertEquals(1, stateBefore.visualObservations.size)
        assertEquals(2, stateBefore.hypotheses.size)
        assertEquals(2, stateBefore.confirmedFacts.size)

        // Privacy action: reset session
        contextRepo.resetFullSession()

        val stateAfter = contextRepo.sessionContext.value
        assertTrue(stateAfter.visualObservations.isEmpty())
        assertFalse(stateAfter.confirmedFacts.any { it.content.contains("sacred tear") })
    }
}
