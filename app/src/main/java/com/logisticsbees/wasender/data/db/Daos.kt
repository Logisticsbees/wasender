package com.logisticsbees.wasender.data.db

import androidx.room.*
import com.logisticsbees.wasender.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    @Query("""
        SELECT c.*,
               COUNT(cc.id)                                              AS total_contacts,
               SUM(CASE WHEN cc.status='sent'    THEN 1 ELSE 0 END)     AS sent_count,
               SUM(CASE WHEN cc.status='failed'  THEN 1 ELSE 0 END)     AS failed_count,
               SUM(CASE WHEN cc.status='pending' THEN 1 ELSE 0 END)     AS pending_count
        FROM campaigns c
        LEFT JOIN campaign_contacts cc ON cc.campaignId = c.id
        GROUP BY c.id
        ORDER BY c.createdAt DESC
    """)
    fun getAllWithStats(): Flow<List<CampaignWithStats>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getById(id: Long): Campaign?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(campaign: Campaign): Long

    @Update
    suspend fun update(campaign: Campaign)

    @Query("UPDATE campaigns SET status=:status, updatedAt=:now WHERE id=:id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(campaign: Campaign)

    @Query("SELECT * FROM campaigns WHERE status='scheduled' AND scheduledAt<=:now")
    suspend fun getDue(now: Long = System.currentTimeMillis()): List<Campaign>
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM campaign_contacts WHERE campaignId=:cid ORDER BY id ASC")
    fun forCampaign(cid: Long): Flow<List<CampaignContact>>

    @Query("SELECT * FROM campaign_contacts WHERE campaignId=:cid AND status='pending' ORDER BY id ASC")
    suspend fun pending(cid: Long): List<CampaignContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<CampaignContact>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: CampaignContact): Long

    @Query("UPDATE campaign_contacts SET status=:s, sentAt=:t, errorMsg=:e WHERE id=:id")
    suspend fun updateStatus(id: Long, s: String, t: Long? = null, e: String? = null)

    @Query("UPDATE campaign_contacts SET status='pending', sentAt=NULL, errorMsg=NULL WHERE campaignId=:cid")
    suspend fun resetAll(cid: Long)

    @Query("DELETE FROM campaign_contacts WHERE campaignId=:cid")
    suspend fun deleteAll(cid: Long)

    @Query("SELECT COUNT(*) FROM campaign_contacts WHERE campaignId=:cid AND status='pending'")
    suspend fun pendingCount(cid: Long): Int
}

@Dao
interface TemplateDao {

    @Query("SELECT * FROM message_templates ORDER BY createdAt DESC")
    fun all(): Flow<List<MessageTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: MessageTemplate): Long

    @Update
    suspend fun update(t: MessageTemplate)

    @Delete
    suspend fun delete(t: MessageTemplate)
}
