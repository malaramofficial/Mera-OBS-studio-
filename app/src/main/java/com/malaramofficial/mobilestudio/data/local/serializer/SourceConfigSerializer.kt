package com.malaramofficial.mobilestudio.data.local.serializer

import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig

/**
 * Lightweight, robust serializer for persisting polymorphic [SourceConfig]
 * without introducing external binary serialization overhead.
 */
object SourceConfigSerializer {

    fun serialize(config: SourceConfig?): String {
        if (config == null) return ""
        return when (config) {
            is SourceConfig.Camera -> "CAM|${config.lensFacing.name}|${config.targetWidth}|${config.targetHeight}|${config.sensorOrientation}"
            is SourceConfig.Screen -> "SCREEN|${config.captureWidth}|${config.captureHeight}|${config.captureFps}|${config.captureSystemAudio}"
            is SourceConfig.Image -> "IMG|${config.scaleMode.name}|${config.alpha}|${config.uri}"
            is SourceConfig.Text -> "TXT|${config.fontSizeSp}|${config.textColorHex}|${config.backgroundColorHex}|${config.fontFamilyName}|${config.text}"
            is SourceConfig.Media -> "MEDIA|${config.isLooping}|${config.autoPlay}|${config.volume}|${config.uri}"
            is SourceConfig.Browser -> "BROWSER|${config.renderFps}|${config.customCss}|${config.url}"
        }
    }

    fun deserialize(raw: String, fallbackType: SourceType): SourceConfig? {
        if (raw.isBlank()) return null
        return try {
            val parts = raw.split("|")
            val typeTag = parts[0]
            when (typeTag) {
                "CAM" -> SourceConfig.Camera(
                    lensFacing = SourceConfig.Camera.LensFacing.valueOf(parts.getOrElse(1) { "BACK" }),
                    targetWidth = parts.getOrElse(2) { "1920" }.toInt(),
                    targetHeight = parts.getOrElse(3) { "1080" }.toInt(),
                    sensorOrientation = parts.getOrElse(4) { "90" }.toInt()
                )
                "SCREEN" -> SourceConfig.Screen(
                    captureWidth = parts.getOrElse(1) { "1920" }.toInt(),
                    captureHeight = parts.getOrElse(2) { "1080" }.toInt(),
                    captureFps = parts.getOrElse(3) { "60" }.toInt(),
                    captureSystemAudio = parts.getOrElse(4) { "true" }.toBoolean()
                )
                "IMG" -> SourceConfig.Image(
                    scaleMode = SourceConfig.Image.ScaleMode.valueOf(parts.getOrElse(1) { "FIT" }),
                    alpha = parts.getOrElse(2) { "1.0" }.toFloat(),
                    uri = parts.drop(3).joinToString("|")
                )
                "TXT" -> SourceConfig.Text(
                    fontSizeSp = parts.getOrElse(1) { "36" }.toFloat(),
                    textColorHex = parts.getOrElse(2) { "0xFFFFFFFF" }.toLong(),
                    backgroundColorHex = parts.getOrElse(3) { "0" }.toLong(),
                    fontFamilyName = parts.getOrElse(4) { "DEFAULT" },
                    text = parts.drop(5).joinToString("|")
                )
                "MEDIA" -> SourceConfig.Media(
                    isLooping = parts.getOrElse(1) { "true" }.toBoolean(),
                    autoPlay = parts.getOrElse(2) { "true" }.toBoolean(),
                    volume = parts.getOrElse(3) { "1.0" }.toFloat(),
                    uri = parts.drop(4).joinToString("|")
                )
                "BROWSER" -> SourceConfig.Browser(
                    renderFps = parts.getOrElse(1) { "30" }.toInt(),
                    customCss = parts.getOrElse(2) { "" },
                    url = parts.drop(3).joinToString("|")
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
