package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.model.IndicativeRiskEngine
import com.example.model.RegionCatalog
import com.example.ui.components.DemoEnvironmentBanner
import com.example.ui.components.EmptyHazardState
import com.example.ui.components.IncidentRowCard
import com.example.ui.components.IndicativeRiskCard
import com.example.ui.components.WeatherContextCard
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskHighRed
import com.example.ui.theme.StatusVerifiedBlue
import com.example.viewmodel.RoadPulseViewModel
import com.example.viewmodel.UiState

@Composable
fun HomeDashboardScreen(
    viewModel: RoadPulseViewModel,
    onOpenMap: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenIncident: (IncidentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsStateWithLifecycle()
    val incidentsState by viewModel.combinedIncidentsState.collectAsStateWithLifecycle()
    val showDemo by viewModel.showDemoData.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()

    val unreadCount = (notificationsState as? UiState.Success)?.data?.count { !it.read } ?: 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. ROADPULSE Top Header & Hero Summary
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_radar),
                        contentDescription = "RoadPulse radar header",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        DeepNavy.copy(alpha = 0.94f),
                                        DeepNavy.copy(alpha = 0.78f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ROADPULSE",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF7DD3FC),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${selectedRegion.name} • ${selectedRegion.stateLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Surface(
                                    color = Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    IconButton(
                                        onClick = onOpenNotifications,
                                        modifier = Modifier.testTag("home_notifications_btn")
                                    ) {
                                        Box {
                                            Icon(
                                                imageVector = Icons.Filled.Notifications,
                                                contentDescription = "Open notifications",
                                                tint = Color.White
                                            )
                                            if (unreadCount > 0) {
                                                Surface(
                                                    color = RiskHighRed,
                                                    shape = RoundedCornerShape(999.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(16.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = unreadCount.coerceAtMost(9).toString(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onOpenReport,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_report_hazard_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddAlert,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Report Hazard", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onOpenMap,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_open_map_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Map,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Hazard Map", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Configurable Region Switcher
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Monitored Regions",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RegionCatalog.regions, key = { it.id }) { region ->
                            FilterChip(
                                selected = region.id == selectedRegion.id,
                                onClick = { viewModel.selectRegion(region.id) },
                                label = { Text(region.name) },
                                modifier = Modifier.testTag("region_chip_${region.id}")
                            )
                        }
                    }
                }
            }

            if (showDemo) {
                item { DemoEnvironmentBanner() }
            }

            // 3. Nearby Hazard Summary KPIs & Indicative Area Risk
            when (val state = incidentsState) {
                is UiState.Loading -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                Text("Loading regional hazard telemetry...")
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val regionIncidents = state.data.filter { it.regionId == selectedRegion.id }
                    val activeCount = regionIncidents.count {
                        it.statusEnum != IncidentStatus.RESOLVED && it.statusEnum != IncidentStatus.REJECTED
                    }
                    val verifiedCount = regionIncidents.count { it.statusEnum == IncidentStatus.VERIFIED }
                    val pendingCount = regionIncidents.count { it.statusEnum == IncidentStatus.PENDING_VERIFICATION }
                    val areaAssessment = IndicativeRiskEngine.assessArea(regionIncidents, weather, selectedRegion.name)

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                label = "Active Hazards",
                                value = activeCount.toString(),
                                icon = Icons.Filled.WarningAmber,
                                accent = RiskHighRed,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                label = "Verified",
                                value = verifiedCount.toString(),
                                icon = Icons.Filled.Verified,
                                accent = StatusVerifiedBlue,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                label = "Pending",
                                value = pendingCount.toString(),
                                icon = Icons.Filled.AddAlert,
                                accent = PrimaryBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        IndicativeRiskCard(
                            assessment = areaAssessment,
                            title = "Indicative Corridor Risk • ${selectedRegion.name}"
                        )
                    }

                    item {
                        WeatherContextCard(
                            weather = weather,
                            isLoading = isWeatherLoading,
                            regionName = selectedRegion.name,
                            onRefresh = { viewModel.refreshWeather() }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Reports in ${selectedRegion.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Open Map →",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryBlue,
                                modifier = Modifier
                                    .clickable(onClick = onOpenMap)
                                    .padding(6.dp)
                            )
                        }
                    }

                    if (regionIncidents.isEmpty()) {
                        item {
                            EmptyHazardState(
                                title = "No Reports Displayed in ${selectedRegion.name}",
                                message = "Absence of incident reports does not guarantee road safety. Unreported potholes, waterlogging, or obstructions may still be present."
                            )
                        }
                    } else {
                        items(regionIncidents.take(8), key = { it.id }) { inc ->
                            IncidentRowCard(
                                incident = inc,
                                onClick = { onOpenIncident(inc) },
                                modifier = Modifier.testTag("home_incident_${inc.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
