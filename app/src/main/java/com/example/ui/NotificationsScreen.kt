package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.HazardCategoryCatalog
import com.example.model.IncidentRecord
import com.example.model.NotificationFrequency
import com.example.model.RegionCatalog
import com.example.ui.components.EmptyHazardState
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.PrimaryBlue
import com.example.viewmodel.RoadPulseViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(
    viewModel: RoadPulseViewModel,
    onOpenIncident: (IncidentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val combinedIncidentsState by viewModel.combinedIncidentsState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Alerts Center") },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Preferences") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (selectedTab == 0) {
                when (val state = notificationsState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                EmptyHazardState(
                                    title = "No Alerts Yet",
                                    message = "Nearby hazard notifications, report status updates, and severe weather advisories will appear here in your in-app notification center."
                                )
                            }
                        } else {
                            val allIncidents = (combinedIncidentsState as? UiState.Success)?.data.orEmpty()
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.data, key = { it.id }) { notif ->
                                    val icon = when (notif.notificationType) {
                                        "WEATHER_ALERT" -> Icons.Filled.Thunderstorm
                                        "STATUS_UPDATE" -> Icons.Filled.Verified
                                        else -> Icons.Filled.Notifications
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (notif.read) {
                                                MaterialTheme.colorScheme.surface
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                            }
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!notif.read) {
                                                    viewModel.markNotificationRead(notif.id)
                                                }
                                                val match = allIncidents.firstOrNull { it.id == notif.incidentId }
                                                if (match != null) {
                                                    onOpenIncident(match)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(icon, contentDescription = null, tint = PrimaryBlue)
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = notif.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = if (notif.read) FontWeight.Medium else FontWeight.Bold
                                                    )
                                                    if (!notif.read) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .background(PrimaryBlue, CircleShape)
                                                        )
                                                    }
                                                }
                                                Text(notif.message, style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    text = "${notif.approximateLocation} • ${formatTimestamp(notif.createdAt?.toDate()?.time)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Notification Preferences Editor
                var radiusKm by remember(prefs) { mutableDoubleStateOf(prefs.alertRadiusKm) }
                var selectedCats by remember(prefs) { mutableStateOf(prefs.enabledCategories.toSet()) }
                var frequency by remember(prefs) {
                    mutableStateOf(NotificationFrequency.fromRaw(prefs.notificationFrequency))
                }
                var watchedRegions by remember(prefs) { mutableStateOf(prefs.watchedRegions.toSet()) }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Alert Radius: ${radiusKm.toInt()} km", style = MaterialTheme.typography.titleSmall)
                                Slider(
                                    value = radiusKm.toFloat(),
                                    onValueChange = { radiusKm = it.toDouble() },
                                    valueRange = 1f..50f
                                )

                                Text("Delivery Frequency", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NotificationFrequency.entries.forEach { freq ->
                                        FilterChip(
                                            selected = frequency == freq,
                                            onClick = { frequency = freq },
                                            label = { Text(freq.label) }
                                        )
                                    }
                                }

                                Text("Watched Regions", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RegionCatalog.regions.forEach { reg ->
                                        val active = reg.id in watchedRegions
                                        FilterChip(
                                            selected = active,
                                            onClick = {
                                                watchedRegions = if (active) watchedRegions - reg.id else watchedRegions + reg.id
                                            },
                                            label = { Text(reg.name) }
                                        )
                                    }
                                }

                                Text("Hazard Categories (${selectedCats.size} selected)", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HazardCategoryCatalog.categories.forEach { cat ->
                                        val active = cat.id in selectedCats
                                        FilterChip(
                                            selected = active,
                                            onClick = {
                                                selectedCats = if (active) selectedCats - cat.id else selectedCats + cat.id
                                            },
                                            label = { Text(cat.label) }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.saveNotificationPreferences(
                                            alertRadiusKm = radiusKm,
                                            enabledCategories = selectedCats.toList(),
                                            frequency = frequency,
                                            watchedRegions = watchedRegions.toList()
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_notif_prefs_btn")
                                ) {
                                    Text("Save Notification Preferences")
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Note: RoadPulse currently delivers alerts via this real-time Firestore In-App Notification Center. Background FCM push delivery requires configuring Firebase Cloud Messaging credentials in Firebase Console.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
