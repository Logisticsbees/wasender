package com.logisticsbees.wasender.service

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.logisticsbees.wasender.R
import com.logisticsbees.wasender.WaSenderApp
import com.logisticsbees.wasender.data.models.CampaignContact
import com.logisticsbees.wasender.ui.campaigns.SendProgressActivity
import kotlinx.coroutines.*
import kotlin.random.Random

class MessageSenderService : Service() {

    companion object {
        const val ACTION_START  = "com.logisticsbees.wasender.START"
        const val ACTION_PAUSE  = "com.logisticsbees.wasender.PAUSE"
        const val ACTION_RESUME = "com.logisticsbees.wasender.RESUME"
        const val ACTION_STOP   = "com.logisticsbees.wasender.STOP"
        const val ACTION_SKIP   = "com.logisticsbees.wasender.SKIP"
        const val EXTRA_CID     = "campaign_id"
        const val NOTIF_ID      = 1001

        // Outbound UI broadcasts
        const val ACTION_PROGRESS = "com.logisticsbees.wasender.PROGRESS"
        const val EXTRA_SENT      = "sent"
        const val EXTRA_FAILED    = "failed"
        const val EXTRA_TOTAL     = "total"
        const val EXTRA_CURRENT   = "current"
        const val EXTRA_DONE      = "done"
    }

    private val db    get() = WaSenderApp.db
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var campaignId = -1L
    private var isPaused   = false
    private var isStopped  = false
    private var skipNext   = false
    private var sentCount  = 0
    private var failedCount = 0
    private var totalCount  = 0

    // Latch to wait for AccessibilityService result per contact
    @Volatile private var waitingForResult = false
    @Volatile private var lastSuccess      = false
    @Volatile private var currentContactId = -1L

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val cId = intent.getLongExtra(WaSenderAccessibilityService.EXTRA_CID, -1L)
            if (cId != currentContactId) return
            lastSuccess = intent.action == WaSenderAccessibilityService.ACTION_SENT
            waitingForResult = false
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val f = IntentFilter().apply {
            addAction(WaSenderAccessibilityService.ACTION_SENT)
            addAction(WaSenderAccessibilityService.ACTION_FAILED)
        }
        registerReceiver(resultReceiver, f)
        startForeground(NOTIF_ID, buildNotif("Preparing…", 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> { campaignId = intent.getLongExtra(EXTRA_CID, -1L); if (campaignId != -1L) startLoop() }
            ACTION_PAUSE  -> isPaused = true
            ACTION_RESUME -> isPaused = false
            ACTION_STOP   -> { isStopped = true; stopSelf() }
            ACTION_SKIP   -> skipNext = true
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(resultReceiver)
        super.onDestroy()
    }

    // ── Main send loop ────────────────────────────────────────────────────────

    private fun startLoop() {
        scope.launch {
            val campaign = db.campaignDao().getById(campaignId) ?: return@launch
            val contacts  = db.contactDao().pending(campaignId)
            totalCount    = contacts.size
            db.campaignDao().updateStatus(campaignId, "running")

            for (contact in contacts) {
                if (isStopped) break
                while (isPaused && !isStopped) delay(500)
                if (isStopped) break

                if (skipNext) {
                    skipNext = false
                    db.contactDao().updateStatus(contact.id, "skipped")
                    broadcastProgress(contact)
                    continue
                }

                val finalMsg = buildMessage(
                    if (!contact.customMessage.isNullOrEmpty()) contact.customMessage else campaign.message,
                    contact
                )

                updateNotif("→ ${contact.displayName}", sentCount, totalCount)
                broadcastProgress(contact)

                sendOne(contact, finalMsg, campaign.useBusinessApp,
                    campaign.delayMinSeconds, campaign.delayMaxSeconds)
            }

            db.campaignDao().updateStatus(campaignId, if (isStopped) "paused" else "completed")
            broadcastDone()
            stopSelf()
        }
    }

    private suspend fun sendOne(
        contact: CampaignContact,
        msg: String,
        biz: Boolean,
        dMin: Int,
        dMax: Int
    ) {
        val acc = WaSenderAccessibilityService.getInstance()
        if (acc == null) {
            db.contactDao().updateStatus(contact.id, "failed", e = "Accessibility service not running")
            failedCount++
            return
        }

        // Wait if the service is still busy from previous contact
        var wait = 0
        while (acc.isBusy && wait < 30) { delay(1000); wait++ }
        if (acc.isBusy) {
            db.contactDao().updateStatus(contact.id, "failed", e = "Timeout waiting for service")
            failedCount++
            return
        }

        currentContactId = contact.id
        waitingForResult  = true
        lastSuccess       = false

        withContext(Dispatchers.Main) { acc.send(contact.phone, msg, contact.id, biz) }

        // Poll for result up to 35 seconds
        var waited = 0
        while (waitingForResult && waited < 35) { delay(1000); waited++ }

        if (lastSuccess) {
            db.contactDao().updateStatus(contact.id, "sent", t = System.currentTimeMillis())
            sentCount++
        } else {
            db.contactDao().updateStatus(contact.id, "failed", e = "Timeout or send error")
            failedCount++
        }

        updateNotif("Sent ${sentCount}/${totalCount}", sentCount, totalCount)

        val delayMs = Random.nextLong(dMin * 1000L, dMax * 1000L)
        delay(delayMs)
    }

    private fun buildMessage(template: String, c: CampaignContact): String =
        template
            .replace("{name}",      c.displayName)
            .replace("{firstName}", c.firstName.ifBlank { c.displayName })
            .replace("{lastName}",  c.lastName)
            .replace("{phone}",     c.phone)

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotif(text: String, sent: Int, total: Int): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SendProgressActivity::class.java).putExtra(EXTRA_CID, campaignId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MessageSenderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WaSenderApp.CHANNEL_ID_SENDER)
            .setContentTitle("WaSender")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_send)
            .setProgress(total, sent, total == 0)
            .setContentIntent(tapIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String, sent: Int, total: Int) =
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotif(text, sent, total))

    // ── Broadcasts ────────────────────────────────────────────────────────────

    private fun broadcastProgress(c: CampaignContact) {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            putExtra(EXTRA_SENT,    sentCount)
            putExtra(EXTRA_FAILED,  failedCount)
            putExtra(EXTRA_TOTAL,   totalCount)
            putExtra(EXTRA_CURRENT, c.displayName)
            setPackage(packageName)
        })
    }

    private fun broadcastDone() {
        sendBroadcast(Intent(ACTION_PROGRESS).apply {
            putExtra(EXTRA_SENT,   sentCount)
            putExtra(EXTRA_FAILED, failedCount)
            putExtra(EXTRA_TOTAL,  totalCount)
            putExtra(EXTRA_DONE,   true)
            setPackage(packageName)
        })
    }
}
