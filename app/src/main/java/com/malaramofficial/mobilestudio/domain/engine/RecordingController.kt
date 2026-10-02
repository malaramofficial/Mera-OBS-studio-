package com.malaramofficial.mobilestudio.domain.engine

import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingConfig
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingState
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingStats
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller interface orchestrating local MP4 multiplexing and recording lifecycle.
 */
interface RecordingController {
    val recordingState: StateFlow<RecordingState>
    val recordingStats: StateFlow<RecordingStats>

    suspend fun startRecording(config: RecordingConfig): AppResult<Unit>
    suspend fun pauseRecording(): AppResult<Unit>
    suspend fun resumeRecording(): AppResult<Unit>
    suspend fun stopRecording(): AppResult<Unit>
}
