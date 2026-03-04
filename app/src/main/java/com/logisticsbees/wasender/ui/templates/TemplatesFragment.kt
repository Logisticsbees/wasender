package com.logisticsbees.wasender.ui.templates

import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.logisticsbees.wasender.data.models.MessageTemplate
import com.logisticsbees.wasender.databinding.FragmentTemplatesBinding
import com.logisticsbees.wasender.databinding.ItemTemplateBinding
import com.logisticsbees.wasender.ui.campaigns.CampaignViewModel
import kotlinx.coroutines.launch

// ── Fragment ──────────────────────────────────────────────────────────────────

class TemplatesFragment : Fragment() {

    private var _b: FragmentTemplatesBinding? = null
    private val b get() = _b!!
    private val vm: CampaignViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTemplatesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TemplateAdapter(
            onEdit   = { dialog(it) },
            onDelete = { vm.deleteTemplate(it) }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.templates.collect { list ->
                    adapter.submitList(list)
                    b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        b.fab.setOnClickListener { dialog(null) }
    }

    private fun dialog(t: MessageTemplate?) {
        val etTitle = EditText(requireContext()).apply {
            hint = "Template title"; setText(t?.title ?: "")
        }
        val etBody = EditText(requireContext()).apply {
            hint = "Message body ({name}, {firstName} supported)"
            setText(t?.body ?: "")
            minLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0)
            addView(etTitle); addView(etBody)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (t == null) "New Template" else "Edit Template")
            .setView(ll)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim()
                val body  = etBody.text.toString().trim()
                if (title.isNotEmpty() && body.isNotEmpty())
                    vm.saveTemplate(title, body, t)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class TemplateAdapter(
    private val onEdit:   (MessageTemplate) -> Unit,
    private val onDelete: (MessageTemplate) -> Unit,
) : ListAdapter<MessageTemplate, TemplateAdapter.VH>(DIFF) {

    inner class VH(val b: ItemTemplateBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: MessageTemplate) {
            b.tvTitle.text  = t.title
            b.tvBody.text   = t.body
            b.btnEdit.setOnClickListener   { onEdit(t)   }
            b.btnDelete.setOnClickListener { onDelete(t) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemTemplateBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<MessageTemplate>() {
            override fun areItemsTheSame(a: MessageTemplate, b: MessageTemplate) = a.id == b.id
            override fun areContentsTheSame(a: MessageTemplate, b: MessageTemplate) = a == b
        }
    }
}
