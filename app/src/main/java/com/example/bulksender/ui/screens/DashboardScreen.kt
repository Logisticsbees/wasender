package com.example.bulksender.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bulksender.data.Campaign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    campaigns: List<Campaign> = emptyList(), // Provide mock data or viewmodel flow later
    onNewCampaignClick: () -> Unit,
    onEnableServiceClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operations Dashboard", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCampaignClick,
                containerColor = MaterialTheme.colorScheme.tertiary, // Green
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, "New Campaign") },
                text = { Text("New Campaign", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            ServiceStatusCard(onEnableServiceClick)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Recent Campaigns",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (campaigns.isEmpty()) {
                    item {
                        Text("No active campaigns.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(campaigns) { campaign ->
                        CampaignCard(campaign)
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceStatusCard(onEnableClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary), // Yellow
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = "Alert", tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Service Disabled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Accessibility access required for automation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Enable")
            }
        }
    }
}

@Composable
fun CampaignCard(campaign: Campaign) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                campaign.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Status: \${campaign.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (campaign.status == "Active") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
