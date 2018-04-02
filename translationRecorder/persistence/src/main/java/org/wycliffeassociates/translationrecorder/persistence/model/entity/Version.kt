package org.wycliffeassociates.translationrecorder.persistence.model.entity

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

/**
 * Created by sarabiaj on 3/28/2018.
 */

@Entity(tableName = "versions")
data class Version(
        @ColumnInfo(name = "id")
        @PrimaryKey(autoGenerate = true)
        val id: Int,
        val name: String,
        val slug: String
)