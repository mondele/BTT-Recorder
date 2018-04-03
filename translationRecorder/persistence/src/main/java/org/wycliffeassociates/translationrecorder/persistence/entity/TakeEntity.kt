package org.wycliffeassociates.translationrecorder.persistence.entity

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey

/**
 * Created by sarabiaj on 3/29/2018.
 */

@Entity(tableName = "takes",
        foreignKeys = [
            ForeignKey(entity = ChunkEntity::class, childColumns = ["chunk_fk"], parentColumns = ["id"]),
            ForeignKey(entity = UserEntity::class, childColumns = ["user_fk"], parentColumns = ["id"])
        ]
)
data class TakeEntity(
        @PrimaryKey(autoGenerate = true)
        val id: Int,
        @ColumnInfo(name = "chunk_fk")
        val chunk: Int,
        @ColumnInfo(name = "user_fk")
        val user: Int,
        val rating: Int,
        val number: Int,
        val file: String,
        val timestamp: Int
)