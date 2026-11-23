package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DemoDataCatalog
import com.example.model.HazardCategoryCatalog
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.ui.components.DemoEnvironmentBanner
import com.example.ui.components.IncidentThumbnailImage
import com.example.ui.components.SeverityRiskPill
import com.example.ui.components.SourcePill
import com.example.ui.components.StatusPill
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskHighRed
import com.example.ui.theme.RiskLowGreen
import com.example.ui.theme.RiskModerateAmber
import com.example.ui.theme.StatusPendingGray
import com.example.viewmodel.RoadPulseViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    viewModel: RoadPulseViewModel,
    onOpenIncident: (IncidentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val adminIncidentsState by viewModel.adminIncidentsState.collectAsStateWithLifecycle()
    val showDemo by viewModel.showDemoData.collectAsStateWithLifecycle()

    if (!isAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = RiskHighRed,
                        modifier = Modifier.size(40.dp)
                    )
                    Text("Administrator Access Restricted", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "This administrative verification & analytics workspace is protected by role-based access control. Your account currently has Public User permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csvContent = viewModel.buildExportCsv(includeDemo = showDemo)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Exported incident CSV successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf<IncidentStatus?>(null) }
    var reviewTarget by remember { mutableStateOf<IncidentRecord?>(null) }

    val realIncidents = (adminIncidentsState as? UiState.Success)?.data.orEmpty()
    val allIncidents = if (showDemo) realIncidents + DemoDataCatalog.sampleIncidents() else realIncidents

    val filteredQueue = remember(allIncidents, searchQuery, filterStatus) {
        allIncidents.filter { inc ->
            val stOk = filterStatus == null || inc.statusEnum == filterStatus
            val q = searchQuery.trim()
            val qOk = q.isEmpty() ||
                inc.title.contains(q, ignoreCase = true) ||
                inc.locationLabel.contains(q, ignoreCase = true) ||
                inc.category.label.contains(q, ignoreCase = true)
            stOk && qOk
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
        ) {
            // Admin Header Bar
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Admin Operations Center", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Verification Queue, Duplicate Detection & Telemetry",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { exportCsvLauncher.launch("roadpulse_incidents.csv") },
                            modifier = Modifier.testTag("admin_export_csv_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CSV")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview & Queue") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Analytics") })
                    }
                }
            }

            if (adminIncidentsState is UiState.Loading && realIncidents.isEmpty() && !showDemo) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showDemo) {
                        item { DemoEnvironmentBanner() }
                    }

                    // Overview Metrics Grid
                    item {
                        val pending = allIncidents.count { it.statusEnum == IncidentStatus.PENDING_VERIFICATION }
                        val verified = allIncidents.count { it.statusEnum == IncidentStatus.VERIFIED }
                        val investigating = allIncidents.count { it.statusEnum == IncidentStatus.UNDER_INVESTIGATION }
                        val resolved = allIncidents.count { it.statusEnum == IncidentStatus.RESOLVED }
                        val rejected = allIncidents.count { it.statusEnum == IncidentStatus.REJECTED }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdminStatBadge("Total", allIncidents.size, PrimaryBlue)
                            AdminStatBadge("Pending", pending, StatusPendingGray)
                            AdminStatBadge("Verified", verified, PrimaryBlue)
                            AdminStatBadge("Investigating", investigating, RiskModerateAmber)
                            AdminStatBadge("Resolved", resolved, RiskLowGreen)
                            AdminStatBadge("Rejected", rejected, RiskHighRed)
                        }
                    }

                    // Queue Search & Status Filter
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search queue by title, road, category...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_search_input")
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = filterStatus == null,
                                    onClick = { filterStatus = null },
                                    label = { Text("All (${allIncidents.size})") }
                                )
                                IncidentStatus.entries.forEach { st ->
                                    FilterChip(
                                        selected = filterStatus == st,
                                        onClick = { filterStatus = if (filterStatus == st) null else st },
                                        label = { Text(st.label) }
                                    )
                                }
                            }
                        }
                    }

                    items(filteredQueue, key = { it.id }) { inc ->
                        val dupes = remember(inc.id, allIncidents) {
                            viewModel.findDuplicatesForIncident(inc)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Text(inc.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = inc.locationLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(status = inc.statusEnum)
                                    SeverityRiskPill(severity = inc.severityEnum)
                                }

                                if (dupes.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.Warning, contentDescription = null, tint = RiskModerateAmber, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "Potential Duplicate (${dupes.size} nearby within 300m)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = RiskModerateAmber
                                            )
                                        }
                                    }
                                }

                                if (inc.aiStatus == "COMPLETED" && !inc.aiCategory.isNullOrBlank()) {
                                    Text(
                                        text = "AI Classification: ${HazardCategoryCatalog.findById(inc.aiCategory).label} (${((inc.aiConfidence ?: 0.0) * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PrimaryBlue
                                    )
                                }

                                if (inc.photoPresent && !inc.thumbnailBase64.isNullOrBlank()) {
                                    IncidentThumbnailImage(
                                        base64 = inc.thumbnailBase64,
                                        contentDescription = inc.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenIncident(inc) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Inspect")
                                    }
                                    Button(
                                        onClick = { reviewTarget = inc },
                                        enabled = !inc.isDemoRecord,
                                        modifier = Modifier.weight(1f).testTag("admin_review_${inc.id}")
                                    ) {
                                        Text(if (inc.isDemoRecord) "Demo Item" else "Review Status")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Analytics Tab with Compose Canvas Charts
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (showDemo) {
                        item { DemoEnvironmentBanner() }
                    }

                    // 1. Category Distribution Bar Chart
                    item {
                        val counts = allIncidents.groupingBy { it.category.label }.eachCount()
                            .entries.sortedByDescending { it.value }.take(8)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Hazard Category Distribution", style = MaterialTheme.typography.titleMedium)
                                if (counts.isEmpty()) {
                                    Text("No incident records stored yet.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    val maxVal = counts.maxOf { it.value }.coerceAtLeast(1)
                                    counts.forEach { (label, count) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(label, style = MaterialTheme.typography.bodySmall)
                                                Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                                                drawRoundRect(
                                                    color = Color(0xFFE2E8F0),
                                                    size = size,
                                                    cornerRadius = CornerRadius(8f, 8f)
                                                )
                                                val fraction = count.toFloat() / maxVal.toFloat()
                                                drawRoundRect(
                                                    color = PrimaryBlue,
                                                    size = Size(size.width * fraction, size.height),
                                                    cornerRadius = CornerRadius(8f, 8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Status Distribution Stacked Bar & Metrics
                    item {
                        val total = allIncidents.size.coerceAtLeast(1)
                        val verifiedOrResolved = allIncidents.count {
                            it.statusEnum == IncidentStatus.VERIFIED || it.statusEnum == IncidentStatus.RESOLVED
                        }
                        val verificationPct = (verifiedOrResolved * 100) / total
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Incident Status Breakdown", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Verified + Resolved Rate: $verificationPct% ($verifiedOrResolved / ${allIncidents.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val segments = listOf(
                                    Triple(IncidentStatus.VERIFIED, PrimaryBlue, allIncidents.count { it.statusEnum == IncidentStatus.VERIFIED }),
                                    Triple(IncidentStatus.PENDING_VERIFICATION, StatusPendingGray, allIncidents.count { it.statusEnum == IncidentStatus.PENDING_VERIFICATION }),
                                    Triple(IncidentStatus.UNDER_INVESTIGATION, RiskModerateAmber, allIncidents.count { it.statusEnum == IncidentStatus.UNDER_INVESTIGATION }),
                                    Triple(IncidentStatus.RESOLVED, RiskLowGreen, allIncidents.count { it.statusEnum == IncidentStatus.RESOLVED }),
                                    Triple(IncidentStatus.REJECTED, RiskHighRed, allIncidents.count { it.statusEnum == IncidentStatus.REJECTED })
                                )
                                Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                                    var xOffset = 0f
                                    segments.forEach { (_, color, count) ->
                                        if (count > 0) {
                                            val w = (count.toFloat() / total.toFloat()) * size.width
                                            drawRect(
                                                color = color,
                                                topLeft = Offset(xOffset, 0f),
                                                size = Size(w, size.height)
                                            )
                                            xOffset += w
                                        }
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    segments.forEach { (st, color, count) ->
                                        Text("${st.label}: $count", style = MaterialTheme.typography.labelSmall, color = color)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Geographical Concentration by Region
                    item {
                        val byRegion = allIncidents.groupingBy { it.regionId }.eachCount()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Geographical Concentration", style = MaterialTheme.typography.titleMedium)
                                byRegion.forEach { (regId, cnt) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(regId, style = MaterialTheme.typography.bodySmall)
                                        Text("$cnt incident(s)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Status Update Dialog
    reviewTarget?.let { target ->
        var chosenStatus by remember(target.id) { mutableStateOf(target.statusEnum) }
        var notes by remember(target.id) { mutableStateOf(target.verificationNotes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text("Update Verification Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(target.title, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IncidentStatus.entries.forEach { st ->
                            FilterChip(
                                selected = chosenStatus == st,
                                onClick = { chosenStatus = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Verification Notes") },
                        placeholder = { Text("Audit rationale or field inspection note...") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminUpdateStatus(target, chosenStatus, notes)
                        reviewTarget = null
                    },
                    modifier = Modifier.testTag("confirm_status_update_btn")
                ) {
                    Text("Save Status")
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminStatBadge(label: String, value: Int, color: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = "$value", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
