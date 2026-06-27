package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val german: String,
    val turkish: String,
    val timestamp: Long = System.currentTimeMillis()
)
