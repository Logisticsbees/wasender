package com.logisticsbees.wasender.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.logisticsbees.wasender.data.models.*

@Database(
    entities = [Campaign::class, CampaignContact::class, MessageTemplate::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun contactDao(): ContactDao
    abstract fun templateDao(): TemplateDao
}
