package com.logisticsbees.wasender.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.logisticsbees.wasender.databinding.*
import com.logisticsbees.wasender.service.WaSenderAccessibilityService
import com.logisticsbees.wasender.utils.CsvImporter
import com.logisticsbees.wasender.utils.PhoneBookImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── ContactsFragment ──────────────────────────────────────────────────────────

class ContactsFragment : Fragment() {

    private var _b: FragmentContactsBinding? = null
    private val b get() = _b!!

    private val permReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startActivity(Intent(requireContext(), GroupExtractorActivity::class.java))
        else Toast.makeText(requireContext(), "Contacts permission needed", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentContactsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnGroupExtractor.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED)
                startActivity(Intent(requireContext(), GroupExtractorActivity::class.java))
            else permReq.launch(Manifest.permission.READ_CONTACTS)
        }
        b.btnSendNonSaved.setOnClickListener {
            startActivity(Intent(requireContext(), SendNonSavedActivity::class.java))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── GroupExtractorActivity ────────────────────────────────────────────────────

class GroupExtractorActivity : AppCompatActivity() {

    private lateinit var b: ActivityGroupExtractorBinding
    private val extracted = mutableListOf<String>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityGroupExtractorBinding.inflate(layoutInflater); setContentView(b.root)
        supportActionBar?.apply { title = "Group Extractor"; setDisplayHomeAsUpEnabled(true) }

        b.btnOpenWa.setOnClickListener {
            (packageManager.getLaunchIntentForPackage(WaSenderAccessibilityService.WA_PKG)
                ?: packageManager.getLaunchIntentForPackage(WaSenderAccessibilityService.WAB_PKG))
                ?.let { startActivity(it) }
                ?: Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }

        b.btnExtract.setOnClickListener {
            val acc  = WaSenderAccessibilityService.getInstance()
            if (acc == null) { Toast.makeText(this, "Accessibility service not running", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val root = acc.rootInActiveWindow
            if (root == null) { Toast.makeText(this, "Open WhatsApp group info screen first", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            extracted.clear()
            scrape(root)
            b.tvCount.text    = "${extracted.size} numbers extracted"
            b.tvNumbers.text  = extracted.joinToString("\n")
        }

        b.btnUseCampaign.setOnClickListener {
            if (extracted.isEmpty()) { Toast.makeText(this, "No numbers extracted", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, com.logisticsbees.wasender.ui.campaigns.CreateCampaignActivity::class.java).apply {
                putStringArrayListExtra("phones", ArrayList(extracted))
            })
        }
    }

    private fun scrape(node: android.view.accessibility.AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: ""
        CsvImporter.normalisePhone(text)?.takeIf { it !in extracted }?.let { extracted.add(it) }
        for (i in 0 until node.childCount) scrape(node.getChild(i) ?: continue)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ── SendNonSavedActivity ──────────────────────────────────────────────────────

class SendNonSavedActivity : AppCompatActivity() {

    private lateinit var b: ActivitySendNonSavedBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivitySendNonSavedBinding.inflate(layoutInflater); setContentView(b.root)
        supportActionBar?.apply { title = "Quick Send"; setDisplayHomeAsUpEnabled(true) }

        b.btnSend.setOnClickListener {
            val phone = b.etPhone.text?.toString()?.trim() ?: ""
            val msg   = b.etMessage.text?.toString()?.trim() ?: ""
            val biz   = b.switchBiz.isChecked
            val norm  = CsvImporter.normalisePhone(phone)
            if (norm == null) { Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val url = "https://wa.me/${norm.replace("+", "")}?text=${Uri.encode(msg)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (biz) setPackage(WaSenderAccessibilityService.WAB_PKG)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { startActivity(intent) }
            catch (e: Exception) { Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
