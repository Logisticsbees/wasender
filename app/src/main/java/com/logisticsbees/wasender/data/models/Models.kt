package com.logisticsbees.wasender.data.models

import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize

@Entity(tableName = "campaigns")
@Parcelize
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val messageType: String = "same",   // "same" | "different"
    val message: String = "",
    val mediaUri: String? = null,
    val mediaType: String? = null,
    val status: String = "draft",       // draft|running|completed|scheduled|paused
    val scheduledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val delayMinSeconds: Int = 5,
    val delayMaxSeconds: Int = 10,
    val useBusinessApp: Boolean = false,
) : Parcelable

@Entity(
    tableName = "campaign_contacts",
    foreignKeys = [ForeignKey(
        entity = Campaign::class,
        parentColumns = ["id"],
        childColumns = ["campaignId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("campaignId")]
)
@Parcelize
data class CampaignContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val phone: String,
    val name: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val customMessage: String? = null,
    val status: String = "pending",     // pending|sent|failed|skipped
    val sentAt: Long? = null,
    val errorMsg: String? = null,
) : Parcelable {
    val displayName: String
        get() = name.ifBlank { listOf(firstName, middleName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phone } }
}

@Entity(tableName = "message_templates")
@Parcelize
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable

data class CampaignWithStats(
    @Embedded val campaign: Campaign,
    @ColumnInfo(name = "total_contacts") val totalContacts: Int = 0,
    @ColumnInfo(name = "sent_count")     val sentCount: Int = 0,
    @ColumnInfo(name = "failed_count")   val failedCount: Int = 0,
    @ColumnInfo(name = "pending_count")  val pendingCount: Int = 0,
)
