package com.malaramofficial.mobilestudio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int,
    val transitionType: String,
    val transitionDurationMs: Long
)
