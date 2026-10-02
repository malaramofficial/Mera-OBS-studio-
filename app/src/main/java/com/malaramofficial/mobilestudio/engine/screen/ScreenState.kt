package com.malaramofficial.mobilestudio.engine.screen

import com.malaramofficial.mobilestudio.core.model.AppError

sealed interface ScreenState {
    data object Idle : ScreenState
    data object RequestingPermission : ScreenState
    data class Active(
        val width: Int,
        val height: Int,
        val dpi: Int,
        val isVerticalShorts: Boolean
    ) : ScreenState
    data object Paused : ScreenState
    data class Error(val error: AppError) : ScreenState

    val isActive: Boolean get() = this is Active
}
