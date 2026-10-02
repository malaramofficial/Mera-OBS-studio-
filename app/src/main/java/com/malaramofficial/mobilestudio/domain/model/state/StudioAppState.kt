package com.malaramofficial.mobilestudio.domain.model.state

import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.permissions.StudioPermissionsState
import com.malaramofficial.mobilestudio.domain.model.audio.AudioMixerState
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingState
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingStats
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Transition
import com.malaramofficial.mobilestudio.domain.model.stream.StreamState
import com.malaramofficial.mobilestudio.domain.model.stream.StreamStats

/**
 * Single immutable state snapshot of the Malaram Mobile Studio application.
 * Observed reactively by the Jetpack Compose presentation layer.
 */
data class StudioAppState(
    val scenes: List<Scene> = emptyList(),
    val previewSceneId: String? = null,
    val programSceneId: String? = null,
    val streamState: StreamState = StreamState.Idle,
    val streamStats: StreamStats = StreamStats(),
    val recordingState: RecordingState = RecordingState.Idle,
    val recordingStats: RecordingStats = RecordingStats(),
    val audioState: AudioMixerState = AudioMixerState(),
    val permissions: StudioPermissionsState = StudioPermissionsState(),
    val transition: Transition = Transition.Cut,
    val activeError: AppError? = null
) {
    val previewScene: Scene?
        get() = scenes.find { it.id == previewSceneId }

    val programScene: Scene?
        get() = scenes.find { it.id == programSceneId }

    val isBroadcastingLive: Boolean
        get() = streamState.isBroadcasting

    val isRecordingToFile: Boolean
        get() = recordingState.isCapturingToFile
}
