package com.example.chat.data

import com.example.chat.model.ChatStreamChunk
import com.example.context.data.GameKnowledgeRepository
import com.example.context.data.InMemoryGameKnowledgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ChatRepository {
    fun sendMessageStream(
        prompt: String,
        gameContext: String,
        tokenDelayMs: Long = 40L
    ): Flow<ChatStreamChunk>
}

class MockChatRepository(
    private val knowledgeRepo: GameKnowledgeRepository = InMemoryGameKnowledgeRepository()
) : ChatRepository {

    override fun sendMessageStream(
        prompt: String,
        gameContext: String,
        tokenDelayMs: Long
    ): Flow<ChatStreamChunk> = flow {
        // Test affordance: trigger error state when prompt contains "!error"
        if (prompt.contains("!error", ignoreCase = true)) {
            delay(150L)
            emit(ChatStreamChunk.Error("Network timeout: Unable to reach game intelligence provider. Please retry."))
            return@flow
        }

        val fullResponse = generateKnowledgeResponse(prompt, gameContext)
        val tokens = fullResponse.split(Regex("(?<=\\s)|(?<=\\n)"))

        try {
            for (token in tokens) {
                delay(tokenDelayMs)
                emit(ChatStreamChunk.Token(token))
            }
            emit(ChatStreamChunk.Complete)
        } catch (e: CancellationException) {
            emit(ChatStreamChunk.Cancelled)
            throw e
        } catch (e: Exception) {
            emit(ChatStreamChunk.Error(e.message ?: "Unexpected streaming error occurred."))
        }
    }

    private fun generateKnowledgeResponse(prompt: String, game: String): String {
        val gameDisplayName = when {
            game.contains("totk", ignoreCase = true) || game.contains("zelda", ignoreCase = true) -> "Zelda: Tears of the Kingdom"
            game.contains("elden", ignoreCase = true) -> "Elden Ring"
            game.contains("hollow", ignoreCase = true) -> "Hollow Knight"
            else -> game
        }

        val matchingGuides = knowledgeRepo.queryGuides(game, prompt)

        if (matchingGuides.isNotEmpty()) {
            val topGuide = matchingGuides.first()
            val stepsFormatted = topGuide.tacticalSteps.mapIndexed { index, step ->
                "${index + 1}. $step"
            }.joinToString("\n")

            return "Authoritative Intel for ${topGuide.title} ($gameDisplayName - ${topGuide.locationOrBoss}):\n\n" +
                    "${topGuide.summary}\n\n" +
                    "Tactical Guidance:\n$stepsFormatted\n\n" +
                    "[Verified Guide: ${topGuide.category}]"
        }

        val lower = prompt.lowercase()
        return when {
            lower.contains("boss") || lower.contains("chefe") -> {
                val bossGuides = knowledgeRepo.queryGuides(game, "", com.example.context.model.GuideCategory.BOSS)
                if (bossGuides.isNotEmpty()) {
                    val guide = bossGuides.first()
                    "Tactical Guidance for $gameDisplayName (${guide.title}):\n" + guide.tacticalSteps.joinToString("\n") { "• $it" }
                } else {
                    "Tactical Guidance for $gameDisplayName Boss:\n1. Observe wind-up animations.\n2. Dodge through active frames.\n3. Punish during recovery frames."
                }
            }
            lower.contains("puzzle") || lower.contains("enigma") -> {
                val puzzleGuides = knowledgeRepo.queryGuides(game, "", com.example.context.model.GuideCategory.PUZZLE)
                if (puzzleGuides.isNotEmpty()) {
                    val guide = puzzleGuides.first()
                    "Puzzle Solution for $gameDisplayName (${guide.title}):\n" + guide.tacticalSteps.joinToString("\n") { "• $it" }
                } else {
                    "Puzzle Clue for $gameDisplayName:\nCheck environmental clues and wall inscriptions. Reversible actions incur no penalty."
                }
            }
            lower.contains("collectible") || lower.contains("item") || lower.contains("seed") || lower.contains("ore") -> {
                val collectibleGuides = knowledgeRepo.queryGuides(game, "", com.example.context.model.GuideCategory.COLLECTIBLE)
                if (collectibleGuides.isNotEmpty()) {
                    val guide = collectibleGuides.first()
                    "Collectible Locations for $gameDisplayName (${guide.title}):\n" + guide.tacticalSteps.joinToString("\n") { "• $it" }
                } else {
                    "Area Intel for $gameDisplayName:\nLook behind hidden waterfalls and breakable walls in early sectors."
                }
            }
            else -> {
                "Contextual Guidance for $gameDisplayName:\nI've loaded the game knowledge base for $gameDisplayName. Ask about specific bosses (e.g. Margit, Malenia, Mantis Lords, Colgera), puzzles, or collectibles in your current area."
            }
        }
    }
}
