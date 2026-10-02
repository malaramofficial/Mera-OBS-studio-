package com.malaramofficial.mobilestudio

import android.app.Application
import com.malaramofficial.mobilestudio.core.permissions.PermissionManager
import com.malaramofficial.mobilestudio.data.datastore.StudioPreferences
import com.malaramofficial.mobilestudio.data.local.StudioDatabase
import com.malaramofficial.mobilestudio.data.repository.SceneRepositoryImpl
import com.malaramofficial.mobilestudio.data.security.SecureCredentialStore
import com.malaramofficial.mobilestudio.domain.engine.SceneManager
import com.malaramofficial.mobilestudio.domain.engine.SourceManager
import com.malaramofficial.mobilestudio.domain.repository.SceneRepository
import com.malaramofficial.mobilestudio.engine.camera.CameraSourceEngine
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import com.malaramofficial.mobilestudio.engine.gpu.VisualTextureSourceManager
import com.malaramofficial.mobilestudio.engine.gpu.MediaTextureSourceManager
import com.malaramofficial.mobilestudio.engine.scene.SceneManagerImpl
import com.malaramofficial.mobilestudio.engine.stream.StudioBroadcastController
import com.malaramofficial.mobilestudio.engine.recording.StudioRecordingController
import com.malaramofficial.mobilestudio.domain.engine.RecordingController
import com.malaramofficial.mobilestudio.engine.source.SourceManagerImpl

/**
 * Application entry point for Malaram Mobile Studio.
 * Initializes persistence singletons and domain engine orchestrators.
 */
class MalaramStudioApplication : Application() {

    lateinit var database: StudioDatabase
        private set

    lateinit var sceneRepository: SceneRepository
        private set

    lateinit var sceneManager: SceneManager
        private set

    lateinit var sourceManager: SourceManager
        private set

    lateinit var studioPreferences: StudioPreferences
        private set

    lateinit var secureCredentialStore: SecureCredentialStore
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var renderPipeline: StudioRenderPipeline
        private set

    lateinit var cameraSourceEngine: CameraSourceEngine
        private set

    lateinit var visualTextureSourceManager: VisualTextureSourceManager
        private set

    lateinit var mediaTextureSourceManager: MediaTextureSourceManager
        private set

    lateinit var broadcastController: StudioBroadcastController
        private set

    lateinit var recordingController: RecordingController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = StudioDatabase.getInstance(this)
        sceneRepository = SceneRepositoryImpl(
            sceneDao = database.sceneDao(),
            sourceDao = database.sourceDao()
        )
        sceneManager = SceneManagerImpl(sceneRepository)
        sourceManager = SourceManagerImpl(sceneManager)
        studioPreferences = StudioPreferences(this)
        secureCredentialStore = SecureCredentialStore(this)
        permissionManager = PermissionManager(this)

        renderPipeline = StudioRenderPipeline()
        cameraSourceEngine = CameraSourceEngine(this)
        visualTextureSourceManager = VisualTextureSourceManager(this, renderPipeline)
        mediaTextureSourceManager = MediaTextureSourceManager(this, renderPipeline)
        broadcastController = StudioBroadcastController(renderPipeline)
        recordingController = StudioRecordingController(this, renderPipeline)
        try {
            renderPipeline.init {
                // OpenGL context and CameraInputSurface initialized
            }
        } catch (t: Throwable) {
            android.util.Log.w("MalaramStudioApp", "RenderPipeline init skipped: ${t.message}")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        cameraSourceEngine.release()
        mediaTextureSourceManager.release()
        renderPipeline.release()
    }

    companion object {
        lateinit var instance: MalaramStudioApplication
            private set
    }
}
