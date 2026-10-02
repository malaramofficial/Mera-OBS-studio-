package com.malaramofficial.mobilestudio.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingConfig
import com.malaramofficial.mobilestudio.domain.model.stream.StreamConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "malaram_studio_prefs")

class StudioPreferences(private val context: Context) {

    private object Keys {
        val RTMP_URL = stringPreferencesKey("rtmp_url")
        val VIDEO_WIDTH = intPreferencesKey("video_width")
        val VIDEO_HEIGHT = intPreferencesKey("video_height")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val LAST_PREVIEW_SCENE_ID = stringPreferencesKey("last_preview_scene_id")
        val LAST_PROGRAM_SCENE_ID = stringPreferencesKey("last_program_scene_id")
    }

    val streamConfigFlow: Flow<StreamConfig> = context.dataStore.data.map { prefs ->
        StreamConfig(
            serverUrl = prefs[Keys.RTMP_URL] ?: "rtmp://a.rtmp.youtube.com/live2",
            streamKey = "", // Stream key is stored securely in SecureCredentialStore
            width = prefs[Keys.VIDEO_WIDTH] ?: 1080,
            height = prefs[Keys.VIDEO_HEIGHT] ?: 1920,
            fps = prefs[Keys.VIDEO_FPS] ?: 30,
            videoBitrateKbps = prefs[Keys.VIDEO_BITRATE] ?: 6000,
            audioBitrateKbps = prefs[Keys.AUDIO_BITRATE] ?: 128
        )
    }

    suspend fun saveStreamConfig(config: StreamConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RTMP_URL] = config.serverUrl
            prefs[Keys.VIDEO_WIDTH] = config.width
            prefs[Keys.VIDEO_HEIGHT] = config.height
            prefs[Keys.VIDEO_FPS] = config.fps
            prefs[Keys.VIDEO_BITRATE] = config.videoBitrateKbps
            prefs[Keys.AUDIO_BITRATE] = config.audioBitrateKbps
        }
    }

    val lastActiveScenesFlow: Flow<Pair<String?, String?>> = context.dataStore.data.map { prefs ->
        Pair(prefs[Keys.LAST_PREVIEW_SCENE_ID], prefs[Keys.LAST_PROGRAM_SCENE_ID])
    }

    suspend fun saveActiveScenes(previewId: String?, programId: String?) {
        context.dataStore.edit { prefs ->
            if (previewId != null) prefs[Keys.LAST_PREVIEW_SCENE_ID] = previewId
            if (programId != null) prefs[Keys.LAST_PROGRAM_SCENE_ID] = programId
        }
    }
}
