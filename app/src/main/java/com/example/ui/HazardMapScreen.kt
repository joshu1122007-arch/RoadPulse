package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LocationHelper
import com.example.model.HazardCategoryCatalog
import com.example.model.HazardSeverity
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.ui.components.DemoEnvironmentBanner
import com.example.ui.components.HazardMapCanvas
import com.example.ui.components.IncidentThumbnailImage
import com.example.ui.components.SeverityRiskPill
import com.example.ui.components.SourcePill
import com.example.ui.components.StatusPill
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskHighRed
import com.example.ui.theme.RiskLowGreen
import com.example.ui.theme.RiskModerateAmber
import com.example.ui.theme.StatusPendingGray
import com.example.viewmodel.DateFilterWindow
import com.example.viewmodel.RoadPulseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HazardMapScreen(
    viewModel: RoadPulseViewModel,
    onOpenIncidentDetail: (IncidentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val region by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val filters by viewModel.mapFilters.collectAsStateWithLifecycle()
    val filteredIncidents by viewModel.filteredMapIncidents.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val showDemo by viewModel.showDemoData.collectAsStateWithLifecycle()

    var selectedIncident by remember { mutableStateOf<IncidentRecord?>(null) }
    var showLegendDialog by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.fetchGpsForDeviceAndReport(context)
        }
    }

    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            viewModel.fetchGpsForDeviceAndReport(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search & Filter Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filters.searchQuery,
                        onValueChange = { q -> viewModel.updateMapFilters { it.copy(searchQuery = q) } },
                        placeholder = { Text("Search road, locality, hazard...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filters.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateMapFilters { it.copy(searchQuery = "") } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )
                    IconButton(
                        onClick = { showFiltersSheet = true },
                        modifier = Modifier.testTag("map_filters_btn")
                    ) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Open hazard filters")
                    }
                    IconButton(
                        onClick = { showLegendDialog = true },
                        modifier = Modifier.testTag("map_legend_btn")
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "Open map legend")
                    }
                }

                // Quick Category Filter Pills
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = filters.selectedCategoryId == null,
                            onClick = { viewModel.updateMapFilters { it.copy(selectedCategoryId = null) } },
                            label = { Text("All (${filteredIncidents.size})") }
                        )
                    }
                    items(HazardCategoryCatalog.categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = filters.selectedCategoryId == cat.id,
                            onClick = {
                                viewModel.updateMapFilters {
                                    it.copy(selectedCategoryId = if (it.selectedCategoryId == cat.id) null else cat.id)
                                }
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }

                if (showDemo) {
                    DemoEnvironmentBanner()
                }
            }
        }

        // Main Interactive Map Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            HazardMapCanvas(
                centerLat = userLocation?.first ?: region.centerLat,
                centerLon = userLocation?.second ?: region.centerLon,
                initialZoom = region.defaultZoom,
                incidents = filteredIncidents,
                selectedIncidentId = selectedIncident?.id,
                userLocation = userLocation,
                onMarkerClick = { selectedIncident = it },
                onRecenterUser = {
                    if (LocationHelper.hasLocationPermission(context)) {
                        viewModel.fetchGpsForDeviceAndReport(context)
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Clear No-Data Overlay State (without implying area is safe)
            if (filteredIncidents.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp, start = 16.dp, end = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "No incidents match current filters",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Adjust filters or pan the map. Absence of displayed markers does not guarantee road safety.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Selected Incident Bottom Sheet
    selectedIncident?.let { inc ->
        ModalBottomSheet(
            onDismissRequest = { selectedIncident = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = inc.category.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = inc.category.color
                    )
                    SourcePill(incident = inc)
                }
                Text(text = inc.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = inc.locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(status = inc.statusEnum)
                    SeverityRiskPill(severity = inc.severityEnum, prefix = "Severity")
                    SeverityRiskPill(severity = inc.riskEnum, prefix = "Indicative Risk")
                }
                if (inc.photoPresent && !inc.thumbnailBase64.isNullOrBlank()) {
                    IncidentThumbnailImage(
                        base64 = inc.thumbnailBase64,
                        contentDescription = inc.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
                Text(
                    text = inc.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
                Button(
                    onClick = {
                        val target = inc
                        selectedIncident = null
                        onOpenIncidentDetail(target)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_view_details_btn")
                ) {
                    Text("View Full Incident & Risk Details")
                }
            }
        }
    }

    // Filters Bottom Sheet
    if (showFiltersSheet) {
        ModalBottomSheet(onDismissRequest = { showFiltersSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter Map Incidents", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        viewModel.updateMapFilters {
                            it.copy(
                                selectedCategoryId = null,
                                selectedStatus = null,
                                selectedSeverity = null,
                                dateWindow = DateFilterWindow.ALL
                            )
                        }
                    }) {
                        Text("Reset All")
                    }
                }

                Text("Verification Status", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IncidentStatus.entries.forEach { st ->
                        FilterChip(
                            selected = filters.selectedStatus == st,
                            onClick = {
                                viewModel.updateMapFilters {
                                    it.copy(selectedStatus = if (it.selectedStatus == st) null else st)
                                }
                            },
                            label = { Text(st.label) }
                        )
                    }
                }

                Text("Severity", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HazardSeverity.entries.forEach { sev ->
                        FilterChip(
                            selected = filters.selectedSeverity == sev,
                            onClick = {
                                viewModel.updateMapFilters {
                                    it.copy(selectedSeverity = if (it.selectedSeverity == sev) null else sev)
                                }
                            },
                            label = { Text(sev.label) }
                        )
                    }
                }

                Text("Time Window", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFilterWindow.entries.forEach { win ->
                        FilterChip(
                            selected = filters.dateWindow == win,
                            onClick = { viewModel.updateMapFilters { it.copy(dateWindow = win) } },
                            label = { Text(win.label) }
                        )
                    }
                }
            }
        }
    }

    // Map Legend Dialog
    if (showLegendDialog) {
        AlertDialog(
            onDismissRequest = { showLegendDialog = false },
            title = { Text("Hazard Map Legend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status Ring Colors:", style = MaterialTheme.typography.labelLarge)
                    LegendRow(PrimaryBlue, "Verified Incident")
                    LegendRow(StatusPendingGray, "Pending Verification")
                    LegendRow(RiskModerateAmber, "Under Investigation")
                    LegendRow(RiskLowGreen, "Resolved")
                    LegendRow(RiskHighRed, "Rejected")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Marker fill color and icon identify the hazard category. Blue pulsing dot indicates your current GPS position when granted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLegendDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, CircleShape)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
