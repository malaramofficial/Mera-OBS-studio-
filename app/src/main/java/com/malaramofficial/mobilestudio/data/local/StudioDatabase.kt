package com.malaramofficial.mobilestudio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.malaramofficial.mobilestudio.data.local.dao.SceneDao
import com.malaramofficial.mobilestudio.data.local.dao.SourceDao
import com.malaramofficial.mobilestudio.data.local.entity.SceneEntity
import com.malaramofficial.mobilestudio.data.local.entity.SourceEntity

@Database(
    entities = [
        SceneEntity::class,
        SourceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun sceneDao(): SceneDao
    abstract fun sourceDao(): SourceDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "malaram_studio_database.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
