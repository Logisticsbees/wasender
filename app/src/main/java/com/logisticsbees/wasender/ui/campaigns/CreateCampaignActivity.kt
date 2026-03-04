package com.logisticsbees.wasender.ui.campaigns

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.logisticsbees.wasender.data.models.CampaignContact
import com.logisticsbees.wasender.databinding.ActivityCreateCampaignBinding
import com.logisticsbees.wasender.utils.CsvImporter
import com.logisticsbees.wasender.utils.ScheduleManager
import java.text.SimpleDateFormat
import java.util.*

class CreateCampaignActivity : AppCompatActivity() {

    private lateinit var b: ActivityCreateCampaignBinding
    private val vm: CampaignViewModel by viewModels()

    private val pending = mutableListOf<CampaignContact>()
    private var savedCid: Long = -1L
    private var mediaUri: Uri? = null
    private var schedule: Calendar? = null

    private val csvPick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        ensureSaved {
            vm.importCsv(uri, savedCid).observe(this) { result ->
                if (result.errors.isNotEmpty())
                    Toast.makeText(this, result.errors.take(3).joinToString("\n"), Toast.LENGTH_LONG).show()
                pending.addAll(result.contacts)
                vm.insertContacts(result.contacts)
                refreshCount()
            }
        }
    }

    private val mediaPick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        mediaUri = uri
        b.tvMediaFile.text = uri.lastPathSegment ?: "File selected"
        b.tvMediaFile.visibility = View.VISIBLE
        b.btnClearMedia.visibility = View.VISIBLE
        val mime = contentResolver.getType(uri) ?: ""
        if (mime.startsWith("image")) {
            b.ivPreview.visibility = View.VISIBLE
            Glide.with(this).load(uri).into(b.ivPreview)
        }
    }

    private val phonePick = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri ?: return@registerForActivityResult
        
        val c = contentResolver.query(uri, null, null, null, null) ?: return@registerForActivityResult
        if (c.moveToFirst()) {
            val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
            
            val name = if (nameIndex != -1) c.getString(nameIndex) ?: "" else ""
            val cid = if (idIndex != -1) c.getString(idIndex) ?: "" else ""
            
            val pc = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(cid), null
            )
            pc?.use { 
                val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (it.moveToFirst() && phoneIndex != -1) {
                    val num = it.getString(phoneIndex) ?: ""
                    CsvImporter.normalisePhone(num)?.let { n ->
                        pending.add(CampaignContact(campaignId = 0, phone = n, name = name))
                    }
                }
            }
        }
        c.close()
        refreshCount()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCreateCampaignBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.apply { title = "New Campaign"; setDisplayHomeAsUpEnabled(true) }
        wire()
    }

    private fun wire() {
        b.btnPickMedia.setOnClickListener { mediaPick.launch("*/*") }
        b.btnClearMedia.setOnClickListener { clearMedia() }
        b.btnPickTemplate.setOnClickListener { pickTemplate() }
        b.btnAddManual.setOnClickListener { addManualDialog() }
        b.btnImportCsv.setOnClickListener { csvPick.launch("text/*") }
        b.btnPhoneBook.setOnClickListener { phonePick.launch(null) }
        b.btnAddQuick.setOnClickListener { addQuick() }
        b.switchSchedule.setOnCheckedChangeListener { _, on ->
            b.layoutSchedule.visibility = if (on) View.VISIBLE else View.GONE
        }
        b.btnPickDate.setOnClickListener { pickDate() }
        b.btnPickTime.setOnClickListener { pickTime() }
        b.btnSaveDraft.setOnClickListener { save(false) }
        b.btnSendNow.setOnClickListener { save(true) }
    }

    private fun save(sendNow: Boolean) {
        val name = b.etName.text?.toString()?.trim() ?: ""
        val message = b.etMessage.text?.toString()?.trim() ?: ""
        val useBiz = b.rbBiz.isChecked
        val msgType = if (b.rbDifferent.isChecked) "different" else "same"
        val dMin = b.etDelayMin.text?.toString()?.toIntOrNull() ?: 5
        val dMax = b.etDelayMax.text?.toString()?.toIntOrNull() ?: 10

        if (name.isBlank()) { toast("Enter campaign name"); return }
        if (message.isBlank() && msgType == "same") { toast("Enter a message"); return }
        if (pending.isEmpty()) { toast("Add at least one contact"); return }

        val sAt = if (b.switchSchedule.isChecked) schedule?.timeInMillis else null

        vm.saveCampaign(name, message, msgType, mediaUri?.toString(), dMin, dMax, useBiz, sAt)
            .observe(this) { cid ->
                vm.insertContacts(pending.map { it.copy(campaignId = cid) })
                if (sAt != null) {
                    ScheduleManager.schedule(this,
                        com.logisticsbees.wasender.data.models.Campaign(
                            id = cid, name = name, message = message, scheduledAt = sAt,
                            useBusinessApp = useBiz, status = "scheduled"
                        )
                    )
                    toast("Campaign scheduled!"); finish()
                } else if (sendNow) {
                    vm.start(cid)
                    startActivity(Intent(this, SendProgressActivity::class.java).apply {
                        putExtra("cid", cid); putExtra("name", name)
                    })
                    finish()
                } else { toast("Draft saved"); finish() }
            }
    }

    private fun addManualDialog() {
        val phone = EditText(this).apply { hint = "+91XXXXXXXXXX"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val name = EditText(this).apply { hint = "Name (optional)" }
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 8, 48, 0)
            addView(phone); addView(name)
        }
        AlertDialog.Builder(this).setTitle("Add Contact").setView(ll)
            .setPositiveButton("Add") { _, _ ->
                val n = CsvImporter.normalisePhone(phone.text.toString().trim())
                if (n == null) toast("Invalid number")
                else { pending.add(CampaignContact(campaignId = 0, phone = n, name = name.text.toString().trim())); refreshCount() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun addQuick() {
        val raw = b.etQuick.text?.toString()?.trim() ?: ""
        val norm = CsvImporter.normalisePhone(raw)
        if (norm == null) { toast("Invalid number"); return }
        pending.add(CampaignContact(campaignId = 0, phone = norm))
        b.etQuick.text?.clear()
        refreshCount()
    }

    private fun pickTemplate() {
        val list = vm.templates.value ?: emptyList()
        if (list.isEmpty()) { toast("No templates saved yet"); return }
        AlertDialog.Builder(this)
            .setTitle("Pick Template")
            .setItems(list.map { it.title }.toTypedArray()) { _, i ->
                b.etMessage.setText(list[i].body)
            }.show()
    }

    private fun pickDate() {
        val c = schedule ?: Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            (schedule ?: Calendar.getInstance().also { schedule = it }).set(y, m, d)
            updateScheduleLabel()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        val c = schedule ?: Calendar.getInstance()
        TimePickerDialog(this, { _, h, min ->
            val sc = schedule ?: Calendar.getInstance().also { schedule = it }
            sc.set(Calendar.HOUR_OF_DAY, h); sc.set(Calendar.MINUTE, min)
            updateScheduleLabel()
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    private fun updateScheduleLabel() {
        b.tvScheduleTime.text = "Scheduled: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(schedule?.time ?: Date())}"
        b.tvScheduleTime.visibility = View.VISIBLE
    }

    private fun clearMedia() {
        mediaUri = null
        b.tvMediaFile.visibility = View.GONE; b.btnClearMedia.visibility = View.GONE
        b.ivPreview.visibility = View.GONE
    }

    private fun refreshCount() { b.tvContactCount.text = "${pending.size} contact(s) added" }

    private fun ensureSaved(then: () -> Unit) {
        if (savedCid != -1L) { then(); return }
        val n = b.etName.text?.toString()?.ifBlank { "Untitled" } ?: "Untitled"
        vm.saveCampaign(n, "").observe(this) { id -> savedCid = id; then() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
