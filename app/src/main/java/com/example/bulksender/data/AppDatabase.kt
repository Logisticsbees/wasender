package com.example.bulksender.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Campaign::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsapp_bulk_sender_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
