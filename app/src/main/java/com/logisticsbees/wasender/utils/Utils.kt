package com.logisticsbees.wasender.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.logisticsbees.wasender.WaSenderApp
import com.logisticsbees.wasender.data.models.Campaign
import com.logisticsbees.wasender.data.models.CampaignContact
import com.logisticsbees.wasender.receiver.ScheduleAlarmReceiver
import com.logisticsbees.wasender.service.MessageSenderService
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────── CSV Importer ────────────────────────────────────

object CsvImporter {

    data class Result(val contacts: List<CampaignContact>, val errors: List<String>)

    fun importFromUri(context: Context, uri: android.net.Uri, campaignId: Long): Result {
        val contacts = mutableListOf<CampaignContact>()
        val errors   = mutableListOf<String>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = CSVReaderBuilder(InputStreamReader(stream)).build()
            val rows   = reader.readAll()
            if (rows.isEmpty()) { errors.add("File is empty"); return Result(contacts, errors) }

            val header    = rows[0].map { it.trim().lowercase() }
            val phoneIdx  = header.indexOfFirst { it in listOf("phone","mobile","number","phonenumber") }
            val nameIdx   = header.indexOfFirst { it in listOf("name","contact_name","contactname") }
            val firstIdx  = header.indexOfFirst { it in listOf("first_name","firstname","fname") }
            val lastIdx   = header.indexOfFirst { it in listOf("last_name","lastname","lname") }
            val msgIdx    = header.indexOfFirst { it == "message" }

            if (phoneIdx == -1) { errors.add("No phone column found"); return Result(contacts, errors) }

            rows.drop(1).forEachIndexed { i, row ->
                val raw   = row.getOrNull(phoneIdx)?.trim() ?: ""
                if (raw.isBlank()) { errors.add("Line ${i+2}: empty phone"); return@forEachIndexed }
                val norm  = normalisePhone(raw) ?: run { errors.add("Line ${i+2}: invalid '$raw'"); return@forEachIndexed }
                contacts.add(CampaignContact(
                    campaignId    = campaignId,
                    phone         = norm,
                    name          = row.getOrNull(nameIdx)?.trim() ?: "",
                    firstName     = row.getOrNull(firstIdx)?.trim() ?: "",
                    lastName      = row.getOrNull(lastIdx)?.trim() ?: "",
                    customMessage = row.getOrNull(msgIdx)?.trim()?.ifBlank { null },
                ))
            }
        } ?: errors.add("Could not open file")

        return Result(contacts, errors)
    }

    fun normalisePhone(raw: String): String? {
        val d = raw.replace(Regex("[^\\d+]"), "")
        if (d.length < 7) return null
        return if (d.startsWith("+")) d else "+$d"
    }
}

// ─────────────────────────── VCF Exporter ────────────────────────────────────

object VcfExporter {
    fun export(context: Context, contacts: List<CampaignContact>, name: String): File {
        val sb = StringBuilder()
        contacts.forEach { c ->
            sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            sb.append("FN:${c.displayName.ifBlank { c.phone }}\r\n")
            if (c.firstName.isNotBlank() || c.lastName.isNotBlank())
                sb.append("N:${c.lastName};${c.firstName};;;\r\n")
            sb.append("TEL;TYPE=CELL:${c.phone}\r\n")
            sb.append("END:VCARD\r\n")
        }
        val file = File(context.cacheDir, "${name.replace(" ", "_")}_contacts.vcf")
        file.writeText(sb.toString())
        return file
    }
}

// ─────────────────────────── Report Exporter ─────────────────────────────────

object ReportExporter {
    fun exportCsv(context: Context, contacts: List<CampaignContact>, name: String): File {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb  = StringBuilder("Name,Phone,Status,SentAt,Error\n")
        contacts.forEach { c ->
            val t = c.sentAt?.let { sdf.format(Date(it)) } ?: ""
            sb.append("\"${c.displayName}\",\"${c.phone}\",\"${c.status}\",\"$t\",\"${c.errorMsg ?: ""}\"\n")
        }
        val file = File(context.cacheDir, "${name.replace(" ", "_")}_report.csv")
        file.writeText(sb.toString())
        return file
    }
}

// ─────────────────────────── Schedule Manager ────────────────────────────────

object ScheduleManager {

    fun schedule(context: Context, campaign: Campaign) {
        val at = campaign.scheduledAt ?: return
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context, campaign.id))
    }

    fun cancel(context: Context, cid: Long) =
        context.getSystemService(AlarmManager::class.java).cancel(pi(context, cid))

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            WaSenderApp.db.campaignDao().getDue().forEach { c ->
                context.startForegroundService(
                    Intent(context, MessageSenderService::class.java).apply {
                        action = MessageSenderService.ACTION_START
                        putExtra(MessageSenderService.EXTRA_CID, c.id)
                    }
                )
            }
        }
    }

    private fun pi(context: Context, cid: Long) = PendingIntent.getBroadcast(
        context, cid.toInt(),
        Intent(context, ScheduleAlarmReceiver::class.java).putExtra(MessageSenderService.EXTRA_CID, cid),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

// ─────────────────────────── Phone Book Importer ─────────────────────────────

object PhoneBookImporter {

    data class PhoneContact(val name: String, val phone: String)

    fun loadAll(context: Context): List<PhoneContact> {
        val results = mutableListOf<PhoneContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cur ->
            val ni = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pi = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cur.moveToNext()) {
                val norm = CsvImporter.normalisePhone(cur.getString(pi) ?: "") ?: continue
                results.add(PhoneContact(cur.getString(ni) ?: "", norm))
            }
        }
        return results
    }
}
