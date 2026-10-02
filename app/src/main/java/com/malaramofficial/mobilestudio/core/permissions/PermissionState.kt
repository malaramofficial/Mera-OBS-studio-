package com.malaramofficial.mobilestudio.core.permissions

/**
 * State representing system runtime permission status for studio capture components.
 */
data class StudioPermissionsState(
    val hasCameraPermission: Boolean = false,
    val hasRecordAudioPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasMediaProjectionPermission: Boolean = false
) {
    val canCaptureLiveAudio: Boolean get() = hasRecordAudioPermission
    val canCaptureCamera: Boolean get() = hasCameraPermission
    val canStreamBackground: Boolean get() = hasNotificationPermission
}
