package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EvidenceRecord
import com.example.model.HazardCategoryCatalog
import com.example.model.HazardSeverity
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.model.IndicativeRiskEngine
import com.example.ui.components.HazardMapCanvas
import com.example.ui.components.IncidentThumbnailImage
import com.example.ui.components.IndicativeRiskCard
import com.example.ui.components.SeverityRiskPill
import com.example.ui.components.SourcePill
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskHighRed
import com.example.viewmodel.RoadPulseViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentDetailScreen(
    incident: IncidentRecord,
    viewModel: RoadPulseViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val liveIncident by viewModel.observeIncident(incident)
        .collectAsStateWithLifecycle(initialValue = incident)
    val activeIncident = liveIncident ?: incident

    val updates by viewModel.observeIncidentUpdates(activeIncident)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val evidenceList by viewModel.observeIncidentEvidence(activeIncident)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val isOwner = activeIncident.reporterId == viewModel.activeUserId
    val canOwnerEdit = !activeIncident.isDemoRecord && isOwner && activeIncident.statusEnum == IncidentStatus.PENDING_VERIFICATION
    val canDeleteIncident = !activeIncident.isDemoRecord && (isAdmin || canOwnerEdit)
    val canAttachEvidence = !activeIncident.isDemoRecord && (isOwner || isAdmin)

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingEvidenceUri by remember { mutableStateOf<Uri?>(null) }
    var evidenceCaptionInput by remember { mutableStateOf("") }
    var captionEditTarget by remember { mutableStateOf<EvidenceRecord?>(null) }
    var captionEditText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingEvidenceUri = uri
            evidenceCaptionInput = ""
        }
    }

    val assessment = IndicativeRiskEngine.assessIncident(
        categoryId = activeIncident.categoryId,
        severity = activeIncident.severityEnum,
        status = activeIncident.statusEnum,
        createdAt = activeIncident.createdAt,
        weather = weather
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (canOwnerEdit) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_incident_btn")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit report")
                        }
                    }
                    if (canDeleteIncident) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("withdraw_incident_btn")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = if (isAdmin && !isOwner) "Delete incident" else "Withdraw report",
                                tint = RiskHighRed
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Summary Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeIncident.category.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = activeIncident.category.color
                            )
                            SourcePill(incident = activeIncident)
                        }
                        Text(text = activeIncident.title, style = MaterialTheme.typography.headlineSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill(status = activeIncident.statusEnum)
                            SeverityRiskPill(severity = activeIncident.severityEnum, prefix = "Severity")
                        }
                        Text(text = activeIncident.description, style = MaterialTheme.typography.bodyMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${activeIncident.locationLabel} (${String.format(Locale.US, "%.4f, %.4f", activeIncident.latitude, activeIncident.longitude)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Reported: ${formatTimestamp(activeIncident.createdAt?.toDate()?.time)} • Updated: ${formatTimestamp(activeIncident.updatedAt?.toDate()?.time)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (canOwnerEdit || canDeleteIncident) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (canOwnerEdit) {
                                    OutlinedButton(
                                        onClick = { showEditDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Edit Report")
                                    }
                                }
                                if (canDeleteIncident) {
                                    OutlinedButton(
                                        onClick = { showDeleteConfirm = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RiskHighRed),
                                        border = BorderStroke(1.dp, RiskHighRed.copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isAdmin && !isOwner) "Delete" else "Withdraw")
                                    }
                                }
                            }
                        }
                    }
                }

                // Primary Photograph Evidence (if attached directly on incident)
                if (!activeIncident.thumbnailBase64.isNullOrBlank()) {
                    IncidentThumbnailImage(
                        base64 = activeIncident.thumbnailBase64,
                        contentDescription = "Photograph for ${activeIncident.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }

                // Evidence & Attachments Subcollection Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = PrimaryBlue)
                                Text("Evidence (${evidenceList.size})", style = MaterialTheme.typography.titleSmall)
                            }
                            if (canAttachEvidence) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("attach_evidence_btn")
                                ) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Attach")
                                }
                            }
                        }

                        if (evidenceList.isEmpty()) {
                            Text(
                                text = "No additional evidence records attached yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            evidenceList.forEach { ev ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!ev.thumbnailBase64.isNullOrBlank()) {
                                            IncidentThumbnailImage(
                                                base64 = ev.thumbnailBase64,
                                                contentDescription = ev.caption.ifBlank { "Evidence item" },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(140.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ev.caption.ifBlank { "Uploaded Evidence (${ev.fileType})" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${ev.fileType} • ${formatTimestamp(ev.uploadedAt?.toDate()?.time)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!activeIncident.isDemoRecord && (isOwner || isAdmin)) {
                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            captionEditTarget = ev
                                                            captionEditText = ev.caption
                                                        }
                                                    ) {
                                                        Icon(Icons.Filled.Edit, contentDescription = "Edit caption", modifier = Modifier.size(18.dp))
                                                    }
                                                    if (isAdmin || (isOwner && activeIncident.statusEnum == IncidentStatus.PENDING_VERIFICATION)) {
                                                        IconButton(
                                                            onClick = { viewModel.deleteIncidentEvidence(activeIncident.id, ev.id) }
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Delete,
                                                                contentDescription = "Remove evidence",
                                                                tint = RiskHighRed,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Map Position Preview
                HazardMapCanvas(
                    centerLat = activeIncident.latitude,
                    centerLon = activeIncident.longitude,
                    initialZoom = 14.5f,
                    incidents = listOf(activeIncident),
                    selectedIncidentId = activeIncident.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                )

                // AI Classification Distinction Card
                if (activeIncident.aiStatus == "COMPLETED" && !activeIncident.aiCategory.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PrimaryBlue)
                                Text("AI-Generated Classification", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "Suggested Category: ${HazardCategoryCatalog.findById(activeIncident.aiCategory).label} • Confidence: ${((activeIncident.aiConfidence ?: 0.0) * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!activeIncident.aiDescription.isNullOrBlank()) {
                                Text(activeIncident.aiDescription, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "AI output is advisory only and is never automatically treated as a verified incident.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Weather Context Card (Recorded or Unavailable)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.WbCloudy, contentDescription = null, tint = PrimaryBlue)
                            Text("Environmental & Weather Context", style = MaterialTheme.typography.titleSmall)
                        }
                        if (!activeIncident.weatherCondition.isNullOrBlank()) {
                            Text(
                                text = "${activeIncident.weatherCondition} • Temp: ${activeIncident.weatherTempC ?: "--"}°C • Rain: ${activeIncident.weatherRainfallMm ?: 0.0} mm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Recorded source: ${activeIncident.weatherSource ?: "Open-Meteo"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (weather.isAvailable) {
                            Text(
                                text = "Regional current weather: ${weather.conditionLabel} • ${weather.temperatureC ?: "--"}°C • ${weather.rainfallMm ?: 0.0} mm (${weather.source})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "Weather observation unavailable at report time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Indicative Risk Assessment
                IndicativeRiskCard(assessment = assessment)

                // Verification Notes & Chronological Status History
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = PrimaryBlue)
                            Text("Verification & Status History", style = MaterialTheme.typography.titleSmall)
                        }
                        if (!activeIncident.verificationNotes.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Admin Note: ${activeIncident.verificationNotes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (updates.isEmpty()) {
                            Text(
                                text = "Initial status: ${activeIncident.statusEnum.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            updates.forEach { upd ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${IncidentStatus.fromRaw(upd.newStatus).label} (${upd.updatedByRole})",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = upd.notes,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = formatTimestamp(upd.createdAt?.toDate()?.time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Edit Pending Incident Dialog
    if (showEditDialog) {
        var editCategoryId by remember(activeIncident.id) { mutableStateOf(activeIncident.categoryId) }
        var editTitle by remember(activeIncident.id) { mutableStateOf(activeIncident.title) }
        var editDescription by remember(activeIncident.id) { mutableStateOf(activeIncident.description) }
        var editSeverity by remember(activeIncident.id) { mutableStateOf(activeIncident.severityEnum) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Pending Report") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Hazard Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Severity", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HazardSeverity.entries.forEach { sev ->
                            FilterChip(
                                selected = editSeverity == sev,
                                onClick = { editSeverity = sev },
                                label = { Text(sev.label) }
                            )
                        }
                    }
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HazardCategoryCatalog.categories.take(6).forEach { cat ->
                            FilterChip(
                                selected = editCategoryId == cat.id,
                                onClick = { editCategoryId = cat.id },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateIncidentDetails(
                            incident = activeIncident,
                            categoryId = editCategoryId,
                            title = editTitle,
                            description = editDescription,
                            severity = editSeverity
                        )
                        showEditDialog = false
                    },
                    enabled = editTitle.trim().length >= 3 && editDescription.trim().length >= 5
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Withdraw / Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (isAdmin && !isOwner) "Delete Incident" else "Withdraw Pending Report") },
            text = {
                Text(
                    if (isAdmin && !isOwner) {
                        "Delete this incident record and its uploaded evidence items permanently?"
                    } else {
                        "Withdraw this pending report? Your submitted report and attached evidence will be removed."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteIncident(activeIncident, onDeleted = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RiskHighRed)
                ) {
                    Text(if (isAdmin && !isOwner) "Delete" else "Withdraw")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // New Evidence Caption Dialog
    pendingEvidenceUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingEvidenceUri = null },
            title = { Text("Attach Photo Evidence") },
            text = {
                OutlinedTextField(
                    value = evidenceCaptionInput,
                    onValueChange = { evidenceCaptionInput = it },
                    label = { Text("Caption (optional)") },
                    placeholder = { Text("Describe what this photo shows...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addIncidentEvidence(context, activeIncident, uri, evidenceCaptionInput)
                        pendingEvidenceUri = null
                    }
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEvidenceUri = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Evidence Caption Dialog
    captionEditTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { captionEditTarget = null },
            title = { Text("Update Evidence Caption") },
            text = {
                OutlinedTextField(
                    value = captionEditText,
                    onValueChange = { captionEditText = it },
                    label = { Text("Caption") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateEvidenceCaption(activeIncident.id, target.id, captionEditText)
                        captionEditTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { captionEditTarget = null }) { Text("Cancel") }
            }
        )
    }
}

