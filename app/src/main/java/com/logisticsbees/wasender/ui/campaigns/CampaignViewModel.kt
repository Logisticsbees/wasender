package com.logisticsbees.wasender.ui.campaigns

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.*
import com.logisticsbees.wasender.WaSenderApp
import com.logisticsbees.wasender.data.models.*
import com.logisticsbees.wasender.service.MessageSenderService
import com.logisticsbees.wasender.utils.CsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CampaignViewModel(app: Application) : AndroidViewModel(app) {

    private val db = WaSenderApp.db

    val campaigns = db.campaignDao().getAllWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates = db.templateDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun contactsFor(cid: Long) = db.contactDao().forCampaign(cid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // ── Campaign CRUD ─────────────────────────────────────────────────────────

    fun saveCampaign(
        name: String, message: String, messageType: String = "same",
        mediaUri: String? = null, delayMin: Int = 5, delayMax: Int = 10,
        useBiz: Boolean = false, scheduledAt: Long? = null,
    ): LiveData<Long> {
        val r = MutableLiveData<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            val id = db.campaignDao().insert(Campaign(
                name = name, message = message, messageType = messageType,
                mediaUri = mediaUri, delayMinSeconds = delayMin, delayMaxSeconds = delayMax,
                useBusinessApp = useBiz, scheduledAt = scheduledAt,
                status = if (scheduledAt != null) "scheduled" else "draft"
            ))
            r.postValue(id)
        }
        return r
    }

    fun delete(c: Campaign) = viewModelScope.launch(Dispatchers.IO) { db.campaignDao().delete(c) }

    // ── Contacts ──────────────────────────────────────────────────────────────

    fun addContact(cid: Long, phone: String, name: String = "", msg: String? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            db.contactDao().insert(CampaignContact(
                campaignId = cid, phone = phone, name = name, customMessage = msg
            ))
        }

    fun insertContacts(list: List<CampaignContact>) =
        viewModelScope.launch(Dispatchers.IO) { db.contactDao().insertAll(list) }

    fun importCsv(uri: Uri, cid: Long): LiveData<CsvImporter.Result> {
        val r = MutableLiveData<CsvImporter.Result>()
        viewModelScope.launch(Dispatchers.IO) {
            r.postValue(CsvImporter.importFromUri(getApplication(), uri, cid))
        }
        return r
    }

    // ── Campaign control ──────────────────────────────────────────────────────

    fun start(cid: Long) = svc(MessageSenderService.ACTION_START) { putExtra(MessageSenderService.EXTRA_CID, cid) }
    fun pause()          = svc(MessageSenderService.ACTION_PAUSE)
    fun resume()         = svc(MessageSenderService.ACTION_RESUME)
    fun stop()           = svc(MessageSenderService.ACTION_STOP)
    fun skip()           = svc(MessageSenderService.ACTION_SKIP)

    fun retry(cid: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.contactDao().resetAll(cid)
        db.campaignDao().updateStatus(cid, "draft")
    }

    private fun svc(action: String, extra: (Intent.() -> Unit)? = null) {
        val ctx = getApplication<WaSenderApp>()
        ctx.startForegroundService(Intent(ctx, MessageSenderService::class.java).apply {
            this.action = action
            extra?.invoke(this)
        })
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    fun saveTemplate(title: String, body: String, existing: MessageTemplate? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            if (existing == null)
                db.templateDao().insert(MessageTemplate(title = title, body = body))
            else
                db.templateDao().update(existing.copy(title = title, body = body))
        }

    fun deleteTemplate(t: MessageTemplate) =
        viewModelScope.launch(Dispatchers.IO) { db.templateDao().delete(t) }
}
