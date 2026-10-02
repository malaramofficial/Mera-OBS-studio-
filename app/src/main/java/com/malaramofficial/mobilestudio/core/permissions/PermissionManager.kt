package com.malaramofficial.mobilestudio.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Concrete permission manager providing accurate check and state observation.
 * Uses real system APIs without mock or fake behavior.
 */
class PermissionManager(private val context: Context) {

    private val _permissionsState = MutableStateFlow(checkAllPermissions())
    val permissionsState: StateFlow<StudioPermissionsState> = _permissionsState.asStateFlow()

    fun refreshPermissions() {
        _permissionsState.value = checkAllPermissions()
    }

    fun isCameraGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isRecordAudioGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkAllPermissions(): StudioPermissionsState {
        return StudioPermissionsState(
            hasCameraPermission = isCameraGranted(),
            hasRecordAudioPermission = isRecordAudioGranted(),
            hasNotificationPermission = isNotificationGranted(),
            hasMediaProjectionPermission = false
        )
    }

    fun updateMediaProjectionGranted(granted: Boolean) {
        _permissionsState.value = _permissionsState.value.copy(
            hasMediaProjectionPermission = granted
        )
    }
}
