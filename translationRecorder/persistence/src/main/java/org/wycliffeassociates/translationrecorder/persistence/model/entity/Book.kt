package org.wycliffeassociates.translationrecorder.persistence.model.entity

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey

/**
 * Created by sarabiaj on 3/28/2018.
 */

@Entity(tableName = "books",
        foreignKeys = [
                ForeignKey(
                        entity = Anthology::class,
                        parentColumns = ["id"],
                        childColumns = ["anthology_fk"],
                        onDelete = ForeignKey.CASCADE
                )
        ]
)
data class Book(
        @PrimaryKey(autoGenerate = true)
        val id: Int,
        val name: String,
        val slug: String,
        val number: Int,
        @ColumnInfo(name = "anthology_fk")
        val anthology: Int
)