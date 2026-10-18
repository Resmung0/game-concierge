package com.example.domain

/**
 * Overlay UI States as specified in Section 5 of Product Requirements.
 */
enum class OverlayUiState {
    COLLAPSED,
    EXPANDED,
    LISTENING,
    THINKING,
    SPEAKING,
    CAPTURING,
    SUSPENDED,
    ERROR;

    fun isInteractive(): Boolean = this != SUSPENDED

    fun isExpanded(): Boolean = this == EXPANDED || this == LISTENING || this == THINKING || this == SPEAKING || this == CAPTURING || this == ERROR
}

/**
 * State machine transition validator enforcing legal transitions between overlay states.
 */
object OverlayStateTransition {
    fun isValid(current: OverlayUiState, target: OverlayUiState): Boolean {
        if (current == target) return true

        return when (current) {
            OverlayUiState.COLLAPSED -> target in setOf(
                OverlayUiState.EXPANDED,
                OverlayUiState.SUSPENDED
            )

            OverlayUiState.EXPANDED -> target in setOf(
                OverlayUiState.COLLAPSED,
                OverlayUiState.LISTENING,
                OverlayUiState.THINKING,
                OverlayUiState.CAPTURING,
                OverlayUiState.SUSPENDED,
                OverlayUiState.ERROR
            )

            OverlayUiState.LISTENING -> target in setOf(
                OverlayUiState.THINKING,
                OverlayUiState.EXPANDED,
                OverlayUiState.COLLAPSED,
                OverlayUiState.ERROR,
                OverlayUiState.SUSPENDED
            )

            OverlayUiState.THINKING -> target in setOf(
                OverlayUiState.SPEAKING,
                OverlayUiState.EXPANDED,
                OverlayUiState.COLLAPSED,
                OverlayUiState.ERROR,
                OverlayUiState.SUSPENDED
            )

            OverlayUiState.SPEAKING -> target in setOf(
                OverlayUiState.EXPANDED,
                OverlayUiState.LISTENING,
                OverlayUiState.COLLAPSED,
                OverlayUiState.ERROR,
                OverlayUiState.SUSPENDED
            )

            OverlayUiState.CAPTURING -> target in setOf(
                OverlayUiState.THINKING,
                OverlayUiState.EXPANDED,
                OverlayUiState.COLLAPSED,
                OverlayUiState.ERROR,
                OverlayUiState.SUSPENDED
            )

            OverlayUiState.ERROR -> target in setOf(
                OverlayUiState.EXPANDED,
                OverlayUiState.COLLAPSED,
                OverlayUiState.SUSPENDED
            )

            OverlayUiState.SUSPENDED -> target in setOf(
                OverlayUiState.COLLAPSED,
                OverlayUiState.EXPANDED
            )
        }
    }
}

/**
 * Representation of the floating capsule screen edge anchor and normalized Y coordinate.
 */
data class CapsulePosition(
    val isAttachedToRight: Boolean = true,
    val normalizedY: Float = 0.35f // 0.0 top, 1.0 bottom of available screen space
)
