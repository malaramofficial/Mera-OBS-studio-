package com.malaramofficial.mobilestudio.core.model

/**
 * Production-grade domain error hierarchy for Malaram Mobile Studio.
 * Separates internal technical diagnostics from localized user-facing messages.
 */
sealed interface AppError {
    val userFriendlyMessage: String
    val technicalDetails: String
    val cause: Throwable?

    sealed interface Permission : AppError {
        data class CameraDenied(val permanently: Boolean = false) : Permission {
            override val userFriendlyMessage: String = "Camera permission is required to stream or record camera video."
            override val technicalDetails: String = "CAMERA permission denied by user (permanent=$permanently)"
            override val cause: Throwable? = null
        }

        data class AudioDenied(val permanently: Boolean = false) : Permission {
            override val userFriendlyMessage: String = "Microphone permission is required to capture voice and studio audio."
            override val technicalDetails: String = "RECORD_AUDIO permission denied by user (permanent=$permanently)"
            override val cause: Throwable? = null
        }

        data class NotificationDenied(val permanently: Boolean = false) : Permission {
            override val userFriendlyMessage: String = "Notification permission is required to maintain the background studio service."
            override val technicalDetails: String = "POST_NOTIFICATIONS permission denied (permanent=$permanently)"
            override val cause: Throwable? = null
        }

        data class ScreenCaptureDenied(val resultCode: Int) : Permission {
            override val userFriendlyMessage: String = "Screen capture permission was declined."
            override val technicalDetails: String = "MediaProjection request was cancelled or denied with code $resultCode"
            override val cause: Throwable? = null
        }

        data class MediaProjectionDenied(val resultCode: Int = -1) : Permission {
            override val userFriendlyMessage: String = "MediaProjection screen capture permission was denied."
            override val technicalDetails: String = "MediaProjection rejected by user or system (code $resultCode)"
            override val cause: Throwable? = null
        }
    }

    sealed interface Camera : AppError {
        data class DeviceInUse(val cameraId: String) : Camera {
            override val userFriendlyMessage: String = "Camera is currently used by another application."
            override val technicalDetails: String = "Camera hardware device $cameraId is locked or in use"
            override val cause: Throwable? = null
        }

        data class Disconnected(val cameraId: String) : Camera {
            override val userFriendlyMessage: String = "Camera was disconnected."
            override val technicalDetails: String = "Camera device $cameraId disconnected from hardware bus"
            override val cause: Throwable? = null
        }

        data class ConfigurationFailed(override val cause: Throwable) : Camera {
            override val userFriendlyMessage: String = "Failed to configure camera preview pipeline."
            override val technicalDetails: String = "Camera session configuration error: ${cause.message}"
        }
    }

    sealed interface Capture : AppError {
        data class VirtualDisplayFailed(override val cause: Throwable?) : Capture {
            override val userFriendlyMessage: String = "Failed to create screen virtual display."
            override val technicalDetails: String = "VirtualDisplay initialization failure: ${cause?.message}"
        }

        data class MediaProjectionRevoked(val reason: String = "Revoked by system") : Capture {
            override val userFriendlyMessage: String = "Screen capture was stopped by the system."
            override val technicalDetails: String = "MediaProjection session terminated: $reason"
            override val cause: Throwable? = null
        }
    }

    sealed interface Audio : AppError {
        data class RecordInitFailed(val sampleRate: Int, override val cause: Throwable? = null) : Audio {
            override val userFriendlyMessage: String = "Audio capture hardware could not be initialized."
            override val technicalDetails: String = "AudioRecord initialization returned uninitialized state for $sampleRate Hz"
        }

        data class BufferUnderrun(val droppedSamples: Int) : Audio {
            override val userFriendlyMessage: String = "Audio stutter detected due to processing load."
            override val technicalDetails: String = "Audio mixer ring buffer underrun: dropped $droppedSamples samples"
            override val cause: Throwable? = null
        }

        data class PlaybackCaptureNotSupported(val reason: String) : Audio {
            override val userFriendlyMessage: String = "Internal game audio capture is not supported on this device/app."
            override val technicalDetails: String = "AudioPlaybackCapture unavailable: $reason"
            override val cause: Throwable? = null
        }
    }

    sealed interface Encoder : AppError {
        data class CodecInitFailed(val mimeType: String, override val cause: Throwable) : Encoder {
            override val userFriendlyMessage: String = "Hardware video/audio encoder could not be created."
            override val technicalDetails: String = "MediaCodec.createEncoderByType($mimeType) failed: ${cause.message}"
        }

        data class SurfaceAllocationFailed(override val cause: Throwable) : Encoder {
            override val userFriendlyMessage: String = "Failed to allocate hardware encoder input surface."
            override val technicalDetails: String = "createInputSurface() failed on MediaCodec: ${cause.message}"
        }

        data class HardwarePressureExceeded(val details: String) : Encoder {
            override val userFriendlyMessage: String = "Device hardware limit reached for simultaneous video encoding."
            override val technicalDetails: String = "Hardware encoder resources exhausted: $details"
            override val cause: Throwable? = null
        }
    }

    sealed interface Streaming : AppError {
        data class DnsLookupFailed(val host: String, override val cause: Throwable? = null) : Streaming {
            override val userFriendlyMessage: String = "Could not resolve streaming server address."
            override val technicalDetails: String = "DNS lookup failed for hostname: $host"
        }

        data class ConnectionRefused(val url: String, override val cause: Throwable? = null) : Streaming {
            override val userFriendlyMessage: String = "Connection to RTMP server was refused. Verify the URL."
            override val technicalDetails: String = "TCP socket connection refused to: $url"
        }

        data class HandshakeFailed(val step: String, override val cause: Throwable? = null) : Streaming {
            override val userFriendlyMessage: String = "RTMP protocol handshake failed."
            override val technicalDetails: String = "RTMP handshake failed during phase: $step"
        }

        data class AuthFailed(val reason: String) : Streaming {
            override val userFriendlyMessage: String = "Stream key or authentication rejected by streaming service."
            override val technicalDetails: String = "RTMP publish authentication rejected: $reason"
            override val cause: Throwable? = null
        }

        data class ConnectionLost(override val cause: Throwable?) : Streaming {
            override val userFriendlyMessage: String = "Stream connection dropped. Attempting to reconnect..."
            override val technicalDetails: String = "Socket disconnected abruptly: ${cause?.message}"
        }
    }

    sealed interface Recording : AppError {
        data class MuxerInitFailed(val path: String, override val cause: Throwable) : Recording {
            override val userFriendlyMessage: String = "Failed to create local recording file container."
            override val technicalDetails: String = "MediaMuxer init failed for $path: ${cause.message}"
        }

        data class WriteFailed(override val cause: Throwable) : Recording {
            override val userFriendlyMessage: String = "Error writing recording data to storage."
            override val technicalDetails: String = "MediaMuxer writeSampleData failed: ${cause.message}"
        }

        data class StorageSpaceExceeded(val remainingBytes: Long) : Recording {
            override val userFriendlyMessage: String = "Recording stopped: Device storage is almost full."
            override val technicalDetails: String = "Available storage bytes ($remainingBytes) fell below safety threshold"
            override val cause: Throwable? = null
        }
    }

    sealed interface Network : AppError {
        data object NoConnectivity : Network {
            override val userFriendlyMessage: String = "No internet connection detected."
            override val technicalDetails: String = "Active NetworkCapabilities has no Internet capability"
            override val cause: Throwable? = null
        }

        data class SocketTimeout(val timeoutMs: Long) : Network {
            override val userFriendlyMessage: String = "Network connection timed out."
            override val technicalDetails: String = "Socket operation timed out after ${timeoutMs}ms"
            override val cause: Throwable? = null
        }
    }

    sealed interface Storage : AppError {
        data class DiskFull(val availableBytes: Long) : Storage {
            override val userFriendlyMessage: String = "Device storage is full."
            override val technicalDetails: String = "Disk space exhausted: available=$availableBytes"
            override val cause: Throwable? = null
        }

        data class PermissionDenied(val path: String) : Storage {
            override val userFriendlyMessage: String = "Storage access was denied."
            override val technicalDetails: String = "Cannot open or create file at path: $path"
            override val cause: Throwable? = null
        }

        data class FileWriteFailed(val path: String, override val cause: Throwable) : Storage {
            override val userFriendlyMessage: String = "Failed to write data to local database/storage."
            override val technicalDetails: String = "Storage write failure at $path: ${cause.message}"
        }
    }

    sealed interface Scene : AppError {
        data class SceneNotFound(val sceneId: String) : Scene {
            override val userFriendlyMessage: String = "Requested scene was not found."
            override val technicalDetails: String = "Scene with id '$sceneId' does not exist in repository"
            override val cause: Throwable? = null
        }

        data object CannotDeleteLastScene : Scene {
            override val userFriendlyMessage: String = "Cannot delete the last remaining scene in the studio."
            override val technicalDetails: String = "Attempted to delete the only remaining scene; at least one scene required"
            override val cause: Throwable? = null
        }

        data class InvalidSceneName(val reason: String) : Scene {
            override val userFriendlyMessage: String = "Invalid scene name: $reason"
            override val technicalDetails: String = "Scene name validation failed: $reason"
            override val cause: Throwable? = null
        }
    }

    sealed interface Source : AppError {
        data class SourceNotFound(val sourceId: String) : Source {
            override val userFriendlyMessage: String = "Source layer not found in active scene."
            override val technicalDetails: String = "Source with id '$sourceId' not found"
            override val cause: Throwable? = null
        }

        data class InvalidTransform(val reason: String) : Source {
            override val userFriendlyMessage: String = "Transform value is invalid: $reason"
            override val technicalDetails: String = "Transform bounds error: $reason"
            override val cause: Throwable? = null
        }

        data class InvalidCrop(val reason: String) : Source {
            override val userFriendlyMessage: String = "Crop dimensions exceed layer boundary."
            override val technicalDetails: String = "Crop validation error: $reason"
            override val cause: Throwable? = null
        }

        data class InvalidLayerOrder(val reason: String) : Source {
            override val userFriendlyMessage: String = "Cannot move layer beyond boundary."
            override val technicalDetails: String = "Z-index layer movement rejected: $reason"
            override val cause: Throwable? = null
        }
    }

    sealed interface Configuration : AppError {
        data class InvalidBitrate(val bitrate: Int, val min: Int, val max: Int) : Configuration {
            override val userFriendlyMessage: String = "Bitrate must be between $min kbps and $max kbps."
            override val technicalDetails: String = "Configured bitrate $bitrate is outside valid range [$min, $max]"
            override val cause: Throwable? = null
        }

        data class UnsupportedResolution(val width: Int, val height: Int) : Configuration {
            override val userFriendlyMessage: String = "Resolution ${width}x$height is not supported by encoder."
            override val technicalDetails: String = "Hardware encoder reported unsupported frame size: ${width}x$height"
            override val cause: Throwable? = null
        }

        data object MissingStreamKey : Configuration {
            override val userFriendlyMessage: String = "Please configure a valid Stream Key before going live."
            override val technicalDetails: String = "Stream key credential was empty or null at publish time"
            override val cause: Throwable? = null
        }
    }

    sealed interface Stream : AppError {
        data object InvalidStreamKey : Stream {
            override val userFriendlyMessage: String = "Please enter a valid YouTube Stream Key to go live."
            override val technicalDetails: String = "Stream key credential was blank or invalid"
            override val cause: Throwable? = null
        }

        data class ConnectionFailed(val reason: String, override val cause: Throwable? = null) : Stream {
            override val userFriendlyMessage: String = "Failed to connect to YouTube RTMP server: $reason"
            override val technicalDetails: String = "RTMP streaming error: $reason"
        }

        data class EncoderInitializationFailed(val reason: String, override val cause: Throwable? = null) : Stream {
            override val userFriendlyMessage: String = "Hardware video encoder could not be started: $reason"
            override val technicalDetails: String = "MediaCodec error: $reason"
        }
    }
}
