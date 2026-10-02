package com.malaramofficial.mobilestudio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    foreignKeys = [
        ForeignKey(
            entity = SceneEntity::class,
            parentColumns = ["id"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sceneId"])]
)
data class SourceEntity(
    @PrimaryKey val id: String,
    val sceneId: String,
    val name: String,
    val type: String,
    val visible: Boolean,
    val locked: Boolean,
    val posX: Float,
    val posY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val scaleX: Float,
    val scaleY: Float,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val opacity: Float,
    val zIndex: Int,
    val chromaEnabled: Boolean,
    val chromaColorHex: Long,
    val chromaSimilarity: Float,
    val chromaSmoothness: Float,
    val chromaSpill: Float,
    val customData: String
)
