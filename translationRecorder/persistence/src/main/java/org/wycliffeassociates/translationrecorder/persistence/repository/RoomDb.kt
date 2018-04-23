package org.wycliffeassociates.translationrecorder.persistence.repository

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import org.wycliffeassociates.translationrecorder.persistence.entity.*
import org.wycliffeassociates.translationrecorder.persistence.repository.dao.*


/**
 * Created by sarabiaj on 4/11/2018.
 */

@Database(entities = arrayOf(
        AnthologyEntity::class,
        BookEntity::class,
        ChapterEntity::class,
        ChunkEntity::class,
        LanguageEntity::class,
        ModeEntity::class,
        ProjectEntity::class,
        TakeEntity::class,
        VersionEntity::class,
        UserEntity::class
), version = 1)
abstract class RoomDb : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun anthologyDao(): AnthologyDao
    abstract fun languageDao(): LanguageDao
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chunkDao(): ChunkDao
    abstract fun modeDao(): ModeDao
    abstract fun projectDao(): ProjectDao
    abstract fun versionDao(): VersionDao
    abstract fun takeDao(): TakeDao

    companion object {
        private var INSTANCE: RoomDb? = null

        fun getInstance(context: Context): RoomDb? {
            if (INSTANCE == null) {
                synchronized(RoomDb::class) {
                    INSTANCE = Room.databaseBuilder(
                                context.getApplicationContext(),
                                RoomDb::class.java, "tr.db"
                            )
                            .allowMainThreadQueries()
                            .build()
                }
            }
            return INSTANCE
        }

        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
