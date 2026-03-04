package com.logisticsbees.wasender

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.logisticsbees.wasender.data.db.AppDatabase

class WaSenderApp : Application() {

    companion object {
        lateinit var db: AppDatabase
            private set
        const val CHANNEL_ID_SENDER   = "wasender_sender"
        const val CHANNEL_ID_SCHEDULE = "wasender_schedule"
    }

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "wasender.db")
            .fallbackToDestructiveMigration()
            .build()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_SENDER,
                "Message Sender",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows bulk send progress" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_SCHEDULE,
                "Scheduled Campaigns",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alerts for scheduled campaigns" }
        )
    }
}
