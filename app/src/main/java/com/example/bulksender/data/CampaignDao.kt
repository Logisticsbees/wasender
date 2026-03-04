package com.example.bulksender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns")
    fun getAllCampaigns(): Flow<List<Campaign>>

    @Insert
    suspend fun insert(campaign: Campaign)
}
