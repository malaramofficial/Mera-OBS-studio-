package com.malaramofficial.mobilestudio.domain.model.scene

/**
 * Visual transition types and parameters between scenes.
 */
data class Transition(
    val type: Type = Type.CUT,
    val durationMs: Long = 300L
) {
    enum class Type {
        CUT,
        CROSSFADE,
        WIPE_LEFT,
        WIPE_RIGHT
    }

    init {
        require(durationMs >= 0L) { "Transition duration cannot be negative" }
    }

    companion object {
        val Cut = Transition(Type.CUT, 0L)
        val DefaultFade = Transition(Type.CROSSFADE, 300L)
    }
}
