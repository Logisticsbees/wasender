package com.example.bulksender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val messageTemplate: String,
    val status: String // Draft, Active, Completed
)
