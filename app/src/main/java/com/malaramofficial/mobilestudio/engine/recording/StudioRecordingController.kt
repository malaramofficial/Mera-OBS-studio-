package com.malaramofficial.mobilestudio.engine.recording

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.os.StatFs
import com.malaramofficial.mobilestudio.core.model.AppError
import com.malaramofficial.mobilestudio.core.model.AppResult
import com.malaramofficial.mobilestudio.domain.engine.RecordingController
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingConfig
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingState
import com.malaramofficial.mobilestudio.domain.model.recording.RecordingStats
import com.malaramofficial.mobilestudio.engine.encoder.H264ProgramEncoder
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import com.malaramofficial.mobilestudio.engine.stream.StudioMicrophoneAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Local MP4 recorder for the Program output.
 *
 * Video and microphone AAC are encoded independently and multiplexed with
 * MediaMuxer. The recorder owns its encoder surfaces, so it can run beside
 * the RTMP program output.
 */
class StudioRecordingController(
    private val context: Context,
    private val renderPipeline: StudioRenderPipeline
) : RecordingController {

    companion object {
        private const val OUTPUT_KEY = "recording"
        private const val MIN_FREE_BYTES = 100L * 1024L * 1024L
        private const val MAX_PENDING_VIDEO_FRAMES = 45
        private const val MAX_PENDING_AUDIO_FRAMES = 120
    }

    private val lock = Any()
    private val encoder = H264ProgramEncoder()
    private val audio = StudioMicrophoneAudio()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingStats = MutableStateFlow(RecordingStats())
    override val recordingStats: StateFlow<RecordingStats> = _recordingStats.asStateFlow()

    private var config: RecordingConfig? = null
    private var outputFile: File? = null
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var startedAtNs = 0L
    private var pausedAtNs = 0L
    private var totalPausedNs = 0L
    private var framesWritten = 0L
    private var bytesWritten = 0L
    private val pendingVideo = ArrayDeque<PendingSample>()
    private val pendingAudio = ArrayDeque<PendingSample>()

    private data class PendingSample(
        val data: ByteArray,
        val info: MediaCodec.BufferInfo
    )

    override suspend fun startRecording(config: RecordingConfig): AppResult<Unit> {
        synchronized(lock) {
            if (_recordingState.value.isCapturingToFile || _recordingState.value is RecordingState.Preparing) {
                return AppResult.Error(AppError.Recording.WriteFailed(
                    IllegalStateException("A recording is already active")
                ))
            }

            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                config.outputDirectoryName.substringAfter("Movies/")
            )
            if (!directory.exists() && !directory.mkdirs()) {
                return AppResult.Error(AppError.Storage.PermissionDenied(directory.absolutePath))
            }

            val free = StatFs(directory.absolutePath).availableBytes
            if (free < MIN_FREE_BYTES) {
                return AppResult.Error(AppError.Recording.StorageSpaceExceeded(free))
            }

            val file = File(
                directory,
                String.format(
                    Locale.US,
                    "%s_%tY%<tm%<td_%<tH%<tM%<tS.mp4",
                    config.filePrefix,
                    java.util.Date()
                )
            )

            _recordingState.value = RecordingState.Preparing
            resetSession(config, file)

            try {
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                val surface = encoder.start(
                    config = H264ProgramEncoder.Config(
                        width = config.width,
                        height = config.height,
                        fps = config.fps,
                        bitrateKbps = config.videoBitrateKbps
                    ),
                    onFormat = { format -> onVideoFormat(format) },
                    onFrame = { frame ->
                        val info = MediaCodec.BufferInfo().apply {
                            set(0, frame.data.size, frame.presentationTimeUs, frame.flags)
                        }
                        onVideoSample(frame.data, info)
                    },
                    onError = { error -> fail(error) }
                )

                renderPipeline.attachProgramOutputSurface(
                    key = OUTPUT_KEY,
                    surface = surface,
                    width = config.width,
                    height = config.height
                )

                audio.start(object : StudioMicrophoneAudio.Listener {
                    override fun onFormat(format: MediaFormat) = onAudioFormat(format)

                    override fun onError(reason: String) {
                        fail(IllegalStateException(reason))
                    }

                    override fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                        val data = ByteArray(info.size)
                        val duplicate = buffer.duplicate()
                        duplicate.position(info.offset)
                        duplicate.limit(info.offset + info.size)
                        duplicate.get(data)
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(0, data.size, info.presentationTimeUs, info.flags)
                        }
                        onAudioSample(data, copyInfo)
                    }
                })

                startedAtNs = System.nanoTime()
                _recordingState.value = RecordingState.Recording(
                    startedTimestamp = System.currentTimeMillis(),
                    outputPath = file.absolutePath
                )
                updateStats()
                return AppResult.Success(Unit)
            } catch (t: Throwable) {
                cleanupAfterFailure()
                return AppResult.Error(AppError.Recording.MuxerInitFailed(file.absolutePath, t))
            }
        }
    }

    override suspend fun pauseRecording(): AppResult<Unit> {
        synchronized(lock) {
            if (_recordingState.value !is RecordingState.Recording) {
                return AppResult.Error(AppError.Recording.WriteFailed(
                    IllegalStateException("Recording is not active")
                ))
            }
            pausedAtNs = System.nanoTime()
            _recordingState.value = RecordingState.Paused(elapsedDurationSeconds())
            return AppResult.Success(Unit)
        }
    }

    override suspend fun resumeRecording(): AppResult<Unit> {
        synchronized(lock) {
            if (_recordingState.value !is RecordingState.Paused) {
                return AppResult.Error(AppError.Recording.WriteFailed(
                    IllegalStateException("Recording is not paused")
                ))
            }
            totalPausedNs += System.nanoTime() - pausedAtNs
            pausedAtNs = 0L
            _recordingState.value = RecordingState.Recording(
                startedTimestamp = System.currentTimeMillis(),
                outputPath = outputFile?.absolutePath.orEmpty()
            )
            return AppResult.Success(Unit)
        }
    }

    override suspend fun stopRecording(): AppResult<Unit> {
        synchronized(lock) {
            if (!_recordingState.value.isCapturingToFile) {
                return AppResult.Error(AppError.Recording.WriteFailed(
                    IllegalStateException("Recording is not active")
                ))
            }

            _recordingState.value = RecordingState.Stopping
            return try {
                audio.stop()
                renderPipeline.detachProgramOutputSurface(OUTPUT_KEY)
                encoder.stop()

                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
                muxer = null

                val file = outputFile
                if (file == null || !file.exists() || file.length() == 0L) {
                    return failAndReturn(IllegalStateException("Recording file was not created"))
                }

                updateStats()
                _recordingState.value = RecordingState.Idle
                clearSession()
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                cleanupAfterFailure()
                AppResult.Error(AppError.Recording.WriteFailed(t))
            }
        }
    }

    private fun onVideoFormat(format: MediaFormat) {
        synchronized(lock) {
            if (_recordingState.value !is RecordingState.Recording && _recordingState.value !is RecordingState.Paused && _recordingState.value !is RecordingState.Preparing) return
            try {
                if (videoTrack < 0) videoTrack = muxer?.addTrack(format) ?: -1
                maybeStartMuxer()
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun onAudioFormat(format: MediaFormat) {
        synchronized(lock) {
            if (_recordingState.value !is RecordingState.Recording && _recordingState.value !is RecordingState.Paused) return
            try {
                if (audioTrack < 0) audioTrack = muxer?.addTrack(format) ?: -1
                maybeStartMuxer()
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun maybeStartMuxer() {
        if (!muxerStarted && videoTrack >= 0 && audioTrack >= 0) {
            muxer?.start()
            muxerStarted = true
            flushPending()
        }
    }

    private fun onVideoSample(data: ByteArray, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (_recordingState.value is RecordingState.Paused) return
            if (_recordingState.value !is RecordingState.Recording && _recordingState.value !is RecordingState.Preparing) return
            if (!muxerStarted) {
                enqueue(pendingVideo, data, info, MAX_PENDING_VIDEO_FRAMES)
                return
            }
            writeSample(videoTrack, data, info)
        }
    }

    private fun onAudioSample(data: ByteArray, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (_recordingState.value is RecordingState.Paused) return
            if (_recordingState.value !is RecordingState.Recording && _recordingState.value !is RecordingState.Preparing) return
            if (!muxerStarted) {
                enqueue(pendingAudio, data, info, MAX_PENDING_AUDIO_FRAMES)
                return
            }
            writeSample(audioTrack, data, info)
        }
    }

    private fun enqueue(queue: ArrayDeque<PendingSample>, data: ByteArray, info: MediaCodec.BufferInfo, max: Int) {
        if (queue.size >= max) queue.removeFirst()
        queue.addLast(PendingSample(data.copyOf(), MediaCodec.BufferInfo().apply {
            set(0, data.size, info.presentationTimeUs, info.flags)
        }))
    }

    private fun flushPending() {
        while (pendingVideo.isNotEmpty()) {
            val sample = pendingVideo.removeFirst()
            writeSample(videoTrack, sample.data, sample.info)
        }
        while (pendingAudio.isNotEmpty()) {
            val sample = pendingAudio.removeFirst()
            writeSample(audioTrack, sample.data, sample.info)
        }
    }

    private fun writeSample(track: Int, data: ByteArray, info: MediaCodec.BufferInfo) {
        if (track < 0 || !muxerStarted) return
        try {
            val adjusted = MediaCodec.BufferInfo().apply {
                val pausedOffsetUs = totalPausedNs / 1_000L
                set(0, data.size, (info.presentationTimeUs - pausedOffsetUs).coerceAtLeast(0L), info.flags)
            }
            muxer?.writeSampleData(track, ByteBuffer.wrap(data), adjusted)
            framesWritten++
            bytesWritten += data.size
            if (framesWritten % 15L == 0L) updateStats()
        } catch (t: Throwable) {
            fail(t)
        }
    }

    private fun fail(error: Throwable) {
        synchronized(lock) {
            if (_recordingState.value is RecordingState.Failed || _recordingState.value == RecordingState.Idle) return
            _recordingState.value = RecordingState.Failed(error.message ?: "Recording failed")
            cleanupAfterFailure()
        }
    }

    private fun cleanupAfterFailure() {
        try { audio.stop() } catch (_: Throwable) {}
        try { renderPipeline.detachProgramOutputSurface(OUTPUT_KEY) } catch (_: Throwable) {}
        try { encoder.stop() } catch (_: Throwable) {}
        try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
        try { muxer?.release() } catch (_: Throwable) {}
        muxer = null
        outputFile?.takeIf { it.exists() && it.length() == 0L }?.delete()
        clearSession()
    }

    private fun failAndReturn(error: Throwable): AppResult.Error {
        _recordingState.value = RecordingState.Failed(error.message ?: "Recording failed")
        cleanupAfterFailure()
        return AppResult.Error(AppError.Recording.WriteFailed(error))
    }

    private fun resetSession(config: RecordingConfig, file: File) {
        this.config = config
        outputFile = file
        videoTrack = -1
        audioTrack = -1
        muxerStarted = false
        startedAtNs = 0L
        pausedAtNs = 0L
        totalPausedNs = 0L
        framesWritten = 0L
        bytesWritten = 0L
        pendingVideo.clear()
        pendingAudio.clear()
        _recordingStats.value = RecordingStats(availableStorageBytes = availableStorage())
    }

    private fun clearSession() {
        config = null
        outputFile = null
        videoTrack = -1
        audioTrack = -1
        muxerStarted = false
        pendingVideo.clear()
        pendingAudio.clear()
    }

    private fun elapsedDurationSeconds(): Long {
        if (startedAtNs == 0L) return 0L
        val end = if (pausedAtNs != 0L) pausedAtNs else System.nanoTime()
        return ((end - startedAtNs - totalPausedNs).coerceAtLeast(0L) / 1_000_000_000L)
    }

    private fun updateStats() {
        _recordingStats.value = RecordingStats(
            durationSeconds = elapsedDurationSeconds(),
            fileSizeBytes = bytesWritten,
            framesWritten = framesWritten,
            availableStorageBytes = availableStorage()
        )
    }

    private fun availableStorage(): Long {
        val dir = outputFile?.parentFile ?: context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return dir?.let { StatFs(it.absolutePath).availableBytes } ?: 0L
    }
}
