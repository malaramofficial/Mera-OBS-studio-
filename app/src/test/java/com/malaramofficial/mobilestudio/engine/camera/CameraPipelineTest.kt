package com.malaramofficial.mobilestudio.engine.camera

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.malaramofficial.mobilestudio.core.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CameraPipelineTest {

    private lateinit var context: Context
    private lateinit var cameraEngine: CameraSourceEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cameraEngine = CameraSourceEngine(context)
    }

    @Test
    fun testInitialCameraStateIsIdle() {
        assertEquals(CameraState.Idle, cameraEngine.cameraState.value)
        assertFalse(cameraEngine.cameraState.value.isActive)
    }

    @Test
    fun testCameraStopTransitionsStateToIdle() {
        cameraEngine.stopCamera()
        assertEquals(CameraState.Idle, cameraEngine.cameraState.value)
    }

    @Test
    fun testCameraConfigAndResolutionDefaults() {
        assertEquals(1920, cameraEngine.currentResolution.width)
        assertEquals(1080, cameraEngine.currentResolution.height)
    }

    @Test
    fun testLensFacingEnumValues() {
        assertEquals(2, LensFacing.entries.size)
        assertTrue(LensFacing.entries.contains(LensFacing.FRONT))
        assertTrue(LensFacing.entries.contains(LensFacing.BACK))
    }

    @Test
    fun testCameraStateErrorEncapsulatesAppError() {
        val error = AppError.Camera.Disconnected("Camera 0 disconnected")
        val state = CameraState.Error(error)

        assertTrue(state.error is AppError.Camera.Disconnected)
        assertEquals("Camera was disconnected.", state.error.userFriendlyMessage)
        assertFalse(state.isActive)
    }
}
