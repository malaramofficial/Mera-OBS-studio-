package com.malaramofficial.mobilestudio.engine.camera

import com.malaramofficial.mobilestudio.core.model.AppError

enum class LensFacing {
    FRONT,
    BACK
}

/**
 * Observable runtime states for the hardware camera pipeline.
 */
sealed interface CameraState {
    data object Idle : CameraState
    data object Starting : CameraState
    data class Active(
        val lensFacing: LensFacing,
        val resolutionWidth: Int,
        val resolutionHeight: Int,
        val fps: Int = 30
    ) : CameraState
    data object Paused : CameraState
    data class Error(val error: AppError) : CameraState

    val isActive: Boolean get() = this is Active
}
