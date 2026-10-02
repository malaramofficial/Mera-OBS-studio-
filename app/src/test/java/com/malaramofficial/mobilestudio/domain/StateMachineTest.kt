package com.malaramofficial.mobilestudio.domain

import com.malaramofficial.mobilestudio.domain.model.recording.RecordingState
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.state.StudioAppState
import com.malaramofficial.mobilestudio.domain.model.stream.StreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTest {

    @Test
    fun testPreviewAndProgramSceneSelection() {
        val sceneA = Scene(id = "scene_a", name = "Scene A")
        val sceneB = Scene(id = "scene_b", name = "Scene B")

        var state = StudioAppState(
            scenes = listOf(sceneA, sceneB),
            previewSceneId = sceneA.id,
            programSceneId = sceneA.id
        )

        assertEquals("Scene A", state.previewScene?.name)
        assertEquals("Scene A", state.programScene?.name)

        // Select new preview
        state = state.copy(previewSceneId = sceneB.id)
        assertEquals("Scene B", state.previewScene?.name)
        assertEquals("Scene A", state.programScene?.name) // Program stays unchanged

        // Execute Cut
        state = state.copy(programSceneId = state.previewSceneId)
        assertEquals("Scene B", state.previewScene?.name)
        assertEquals("Scene B", state.programScene?.name) // Program now matches preview
    }

    @Test
    fun testStreamStateTransitions() {
        var streamState: StreamState = StreamState.Idle
        assertFalse(streamState.isBroadcasting)

        streamState = StreamState.Preparing
        assertFalse(streamState.isBroadcasting)

        streamState = StreamState.Connecting
        assertFalse(streamState.isBroadcasting)

        streamState = StreamState.Live(startedTimestamp = 1000L)
        assertTrue(streamState.isBroadcasting)

        // Socket drops -> reconnecting
        streamState = StreamState.Reconnecting(attempt = 1, maxAttempts = 5)
        assertTrue(streamState.isBroadcasting) // Still considered an active broadcast attempt

        // Max retries failed
        streamState = StreamState.Failed("Connection lost", canRetry = true)
        assertFalse(streamState.isBroadcasting)

        streamState = StreamState.Stopping
        assertFalse(streamState.isBroadcasting)

        streamState = StreamState.Idle
        assertFalse(streamState.isBroadcasting)
    }

    @Test
    fun testRecordingStateTransitions() {
        var recState: RecordingState = RecordingState.Idle
        assertFalse(recState.isCapturingToFile)

        recState = RecordingState.Preparing
        assertFalse(recState.isCapturingToFile)

        recState = RecordingState.Recording(startedTimestamp = 2000L, outputPath = "/test.mp4")
        assertTrue(recState.isCapturingToFile)

        recState = RecordingState.Paused(elapsedDurationSeconds = 120L)
        assertTrue(recState.isCapturingToFile)

        recState = RecordingState.Stopping
        assertFalse(recState.isCapturingToFile)

        recState = RecordingState.Idle
        assertFalse(recState.isCapturingToFile)
    }
}
