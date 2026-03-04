package com.logisticsbees.wasender.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.logisticsbees.wasender.service.MessageSenderService
import com.logisticsbees.wasender.utils.ScheduleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.logisticsbees.wasender.WaSenderApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleManager.rescheduleAll(context)
        }
    }
}

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cid = intent.getLongExtra(MessageSenderService.EXTRA_CID, -1L)
        if (cid == -1L) return
        context.startForegroundService(
            Intent(context, MessageSenderService::class.java).apply {
                action = MessageSenderService.ACTION_START
                putExtra(MessageSenderService.EXTRA_CID, cid)
            }
        )
    }
}
