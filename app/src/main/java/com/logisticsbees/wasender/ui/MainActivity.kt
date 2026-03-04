package com.logisticsbees.wasender.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.logisticsbees.wasender.R
import com.logisticsbees.wasender.databinding.ActivityMainBinding
import com.logisticsbees.wasender.service.WaSenderAccessibilityService
import com.logisticsbees.wasender.ui.campaigns.CampaignListFragment
import com.logisticsbees.wasender.ui.contacts.ContactsFragment
import com.logisticsbees.wasender.ui.templates.TemplatesFragment

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_campaigns -> showFrag(CampaignListFragment())
                R.id.nav_contacts  -> showFrag(ContactsFragment())
                R.id.nav_templates -> showFrag(TemplatesFragment())
            }
            true
        }

        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.nav_campaigns
        }
    }

    override fun onResume() {
        super.onResume()
        if (!WaSenderAccessibilityService.isEnabled(this)) promptAccessibility()
    }

    private fun showFrag(f: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
    }

    private fun promptAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "WaSender needs the Accessibility Service to automatically send WhatsApp messages.\n\n" +
                "Path: Settings → Accessibility → Downloaded Apps → WaSender → Enable"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
