package com.example.overlay

import com.example.domain.CapsulePosition
import com.example.domain.OverlayStateTransition
import com.example.domain.OverlayUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized state coordinator for the Game Concierge overlay.
 * Coordinates UI state, dragging coordinates, and service running state.
 */
object OverlayStateManager {

    private val _uiState = MutableStateFlow(OverlayUiState.SUSPENDED)
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _capsulePosition = MutableStateFlow(CapsulePosition())
    val capsulePosition: StateFlow<CapsulePosition> = _capsulePosition.asStateFlow()

    private val _activeGame = MutableStateFlow("Select Game")
    val activeGame: StateFlow<String> = _activeGame.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun requestTransition(target: OverlayUiState): Boolean {
        val current = _uiState.value
        return if (OverlayStateTransition.isValid(current, target)) {
            _uiState.value = target
            true
        } else {
            false
        }
    }

    fun forceState(state: OverlayUiState) {
        _uiState.value = state
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _uiState.value = OverlayUiState.SUSPENDED
        } else if (_uiState.value == OverlayUiState.SUSPENDED) {
            _uiState.value = OverlayUiState.COLLAPSED
        }
    }

    fun updatePosition(isAttachedToRight: Boolean, normalizedY: Float) {
        _capsulePosition.value = CapsulePosition(
            isAttachedToRight = isAttachedToRight,
            normalizedY = normalizedY.coerceIn(0.05f, 0.90f)
        )
    }

    fun setActiveGame(gameTitle: String) {
        _activeGame.value = gameTitle
    }

    fun setStatusMessage(msg: String?) {
        _statusMessage.value = msg
    }
}
