package com.logisticsbees.wasender.ui.campaigns

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.logisticsbees.wasender.R
import com.logisticsbees.wasender.WaSenderApp
import com.logisticsbees.wasender.data.models.CampaignContact
import com.logisticsbees.wasender.databinding.ActivitySendProgressBinding
import com.logisticsbees.wasender.databinding.ActivityCampaignReportBinding
import com.logisticsbees.wasender.databinding.ItemReportContactBinding
import com.logisticsbees.wasender.service.MessageSenderService
import com.logisticsbees.wasender.utils.ReportExporter
import com.logisticsbees.wasender.utils.VcfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── SendProgressActivity ──────────────────────────────────────────────────────

class SendProgressActivity : AppCompatActivity() {

    private lateinit var b: ActivitySendProgressBinding
    private val vm: CampaignViewModel by viewModels()
    private var paused = false

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val sent    = intent.getIntExtra(MessageSenderService.EXTRA_SENT, 0)
            val failed  = intent.getIntExtra(MessageSenderService.EXTRA_FAILED, 0)
            val total   = intent.getIntExtra(MessageSenderService.EXTRA_TOTAL, 0)
            val current = intent.getStringExtra(MessageSenderService.EXTRA_CURRENT) ?: ""
            val done    = intent.getBooleanExtra(MessageSenderService.EXTRA_DONE, false)
            render(sent, failed, total, current, done)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivitySendProgressBinding.inflate(layoutInflater); setContentView(b.root)
        b.tvTitle.text = intent.getStringExtra("name") ?: "Sending"

        registerReceiver(rx, IntentFilter(MessageSenderService.ACTION_PROGRESS))

        b.btnPause.setOnClickListener {
            paused = !paused
            if (paused) { vm.pause(); b.btnPause.text = "Resume" }
            else        { vm.resume(); b.btnPause.text = "Pause" }
        }
        b.btnSkip.setOnClickListener  { vm.skip() }
        b.btnStop.setOnClickListener  { vm.stop(); finish() }
    }

    private fun render(sent: Int, failed: Int, total: Int, current: String, done: Boolean) {
        b.tvSent.text    = sent.toString()
        b.tvFailed.text  = failed.toString()
        b.tvPending.text = (total - sent - failed).coerceAtLeast(0).toString()
        b.tvProgress.text = "$sent / $total"
        b.tvCurrent.text  = if (done) "✅ All done!" else "→ $current"
        if (total > 0) b.progressBar.progress = ((sent + failed) * 100 / total)
        if (done) { b.btnPause.isEnabled = false; b.btnSkip.isEnabled = false }
    }

    override fun onDestroy() { unregisterReceiver(rx); super.onDestroy() }
}

// ── CampaignReportActivity ────────────────────────────────────────────────────

class CampaignReportActivity : AppCompatActivity() {

    private lateinit var b: ActivityCampaignReportBinding
    private val vm: CampaignViewModel by viewModels()
    private var cid  = -1L
    private var name = ""
    private var contacts: List<CampaignContact> = emptyList()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityCampaignReportBinding.inflate(layoutInflater); setContentView(b.root)
        cid  = intent.getLongExtra("cid", -1L)
        name = intent.getStringExtra("name") ?: "Campaign"
        supportActionBar?.apply { title = "$name — Report"; setDisplayHomeAsUpEnabled(true) }

        val adapter = ReportAdapter()
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        lifecycleScope.launch {
            contacts = WaSenderApp.db.contactDao().forCampaign(cid).first()
            adapter.submitList(contacts)
            val s = contacts.count { it.status == "sent" }
            val f = contacts.count { it.status == "failed" }
            val p = contacts.count { it.status == "pending" }
            b.tvStats.text = "Total: ${contacts.size}  ✅ $s  ❌ $f  ⏳ $p"
        }

        b.btnExportCsv.setOnClickListener { share("csv") }
        b.btnExportVcf.setOnClickListener { share("vcf") }
        b.btnResend.setOnClickListener {
            vm.retry(cid)
            vm.start(cid)
            startActivity(Intent(this, SendProgressActivity::class.java).apply {
                putExtra("cid", cid); putExtra("name", name)
            })
        }
    }

    private fun share(type: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = if (type == "csv") ReportExporter.exportCsv(applicationContext, contacts, name)
                       else               VcfExporter.export(applicationContext, contacts, name)
            val uri  = FileProvider.getUriForFile(applicationContext, "$packageName.fileprovider", file)
            withContext(Dispatchers.Main) {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        this.type = if (type == "csv") "text/csv" else "text/vcard"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Export"
                ))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ── ReportAdapter ─────────────────────────────────────────────────────────────

class ReportAdapter : ListAdapter<CampaignContact, ReportAdapter.VH>(DIFF) {

    class VH(val b: ItemReportContactBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: CampaignContact) {
            b.tvName.text   = c.displayName
            b.tvPhone.text  = c.phone
            b.tvStatus.text = c.status.replaceFirstChar { it.uppercase() }
            b.tvStatus.setTextColor(when (c.status) {
                "sent"    -> Color.parseColor("#4CAF50")
                "failed"  -> Color.parseColor("#F44336")
                "skipped" -> Color.parseColor("#9E9E9E")
                else      -> Color.parseColor("#FF9800")
            })
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemReportContactBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CampaignContact>() {
            override fun areItemsTheSame(a: CampaignContact, b: CampaignContact) = a.id == b.id
            override fun areContentsTheSame(a: CampaignContact, b: CampaignContact) = a == b
        }
    }
}
