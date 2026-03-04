package com.example.bulksender

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.bulksender.ui.screens.CampaignScreen
import com.example.bulksender.ui.screens.DashboardScreen
import com.example.bulksender.ui.theme.WhatsAppBulkSenderTheme

enum class Screen { Dashboard, SetupCampaign }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatsAppBulkSenderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

                    when (currentScreen) {
                        Screen.Dashboard -> {
                            DashboardScreen(
                                onNewCampaignClick = { currentScreen = Screen.SetupCampaign },
                                onEnableServiceClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    startActivity(intent)
                                }
                            )
                        }
                        Screen.SetupCampaign -> {
                            CampaignScreen(
                                onBackClick = { currentScreen = Screen.Dashboard },
                                onStartCampaign = { name, message ->
                                    // Save to DB and kick off service worker here
                                    currentScreen = Screen.Dashboard 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
