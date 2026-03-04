package com.logisticsbees.wasender.ui.campaigns

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.logisticsbees.wasender.data.models.CampaignWithStats
import com.logisticsbees.wasender.databinding.FragmentCampaignListBinding
import com.logisticsbees.wasender.databinding.ItemCampaignBinding
import kotlinx.coroutines.launch

// ── Fragment ──────────────────────────────────────────────────────────────────

class CampaignListFragment : Fragment() {

    private var _b: FragmentCampaignListBinding? = null
    private val b get() = _b!!
    private val vm: CampaignViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCampaignListBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = CampaignAdapter(
            onStart  = { vm.start(it.campaign.id) },
            onReport = {
                startActivity(Intent(requireContext(), CampaignReportActivity::class.java).apply {
                    putExtra("cid",  it.campaign.id)
                    putExtra("name", it.campaign.name)
                })
            },
            onDelete = { vm.delete(it.campaign) }
        )

        b.recyclerCampaigns.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerCampaigns.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.campaigns.collect { list ->
                    adapter.submitList(list)
                    b.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        b.fabNewCampaign.setOnClickListener {
            startActivity(Intent(requireContext(), CreateCampaignActivity::class.java))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class CampaignAdapter(
    private val onStart:  (CampaignWithStats) -> Unit,
    private val onReport: (CampaignWithStats) -> Unit,
    private val onDelete: (CampaignWithStats) -> Unit,
) : ListAdapter<CampaignWithStats, CampaignAdapter.VH>(DIFF) {

    inner class VH(val b: ItemCampaignBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CampaignWithStats) {
            b.tvCampaignName.text = item.campaign.name
            b.tvTotal.text   = "Total\n${item.totalContacts}"
            b.tvSent.text    = "Sent\n${item.sentCount}"
            b.tvFailed.text  = "Failed\n${item.failedCount}"
            b.tvPending.text = "Pending\n${item.pendingCount}"
            val (label, color) = when (item.campaign.status) {
                "running"   -> "Running"   to Color.parseColor("#2196F3")
                "completed" -> "Done"      to Color.parseColor("#4CAF50")
                "scheduled" -> "Scheduled" to Color.parseColor("#FF9800")
                "paused"    -> "Paused"    to Color.parseColor("#9E9E9E")
                else        -> "Draft"     to Color.parseColor("#607D8B")
            }
            b.tvStatus.text = label
            b.tvStatus.setBackgroundColor(color)
            b.btnStart.isEnabled  = item.campaign.status !in listOf("running")
            b.btnStart.setOnClickListener  { onStart(item)  }
            b.btnReport.setOnClickListener { onReport(item) }
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemCampaignBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CampaignWithStats>() {
            override fun areItemsTheSame(a: CampaignWithStats, b: CampaignWithStats) = a.campaign.id == b.campaign.id
            override fun areContentsTheSame(a: CampaignWithStats, b: CampaignWithStats) = a == b
        }
    }
}
