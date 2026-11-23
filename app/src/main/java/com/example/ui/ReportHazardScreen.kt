package com.example.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AiClassificationState
import com.example.data.ImageUtils
import com.example.data.LocationHelper
import com.example.model.HazardCategoryCatalog
import com.example.model.HazardGroup
import com.example.model.HazardSeverity
import com.example.ui.components.HazardMapCanvas
import com.example.ui.components.SeverityRiskPill
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskLowContainer
import com.example.ui.theme.RiskLowGreen
import com.example.ui.theme.RiskModerateAmber
import com.example.ui.theme.RiskModerateContainer
import com.example.viewmodel.RoadPulseViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportHazardScreen(
    viewModel: RoadPulseViewModel,
    onTrackReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val draft by viewModel.reportDraft.collectAsStateWithLifecycle()
    val region by viewModel.selectedRegion.collectAsStateWithLifecycle()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var permissionDeniedNote by remember { mutableStateOf<String?>(null) }
    var showPreviewStep by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoSelected(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onPhotoSelected(context, uri)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = null
            viewModel.fetchGpsForDeviceAndReport(context)
        } else {
            permissionDeniedNote = "Location permission was denied. Tap directly on the map below to set exact hazard coordinates."
        }
    }

    // Confirmation Screen
    val submittedId = draft.submittedIncidentId
    if (submittedId != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(color = RiskLowContainer, shape = RoundedCornerShape(999.dp)) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Report submitted",
                            tint = RiskLowGreen,
                            modifier = Modifier.padding(14.dp).size(40.dp)
                        )
                    }
                    Text("Hazard Report Submitted", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Report ID: $submittedId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Assigned initial status: Pending Verification. RoadPulse administrators will review your submission and evidence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            viewModel.resetReportDraft()
                            showPreviewStep = false
                            onTrackReports()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("track_submitted_report_btn")
                    ) {
                        Text("Track My Reports")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.resetReportDraft()
                            showPreviewStep = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Submit Another Hazard")
                    }
                }
            }
        }
        return
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Report a Road Hazard", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Submit citizen road safety or disaster observations. All new reports start as Pending Verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Photograph & Gemini AI Triage
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("1. Photograph & AI-Assisted Triage", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("pick_photo_btn")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery")
                        }
                        OutlinedButton(
                            onClick = {
                                val uri = ImageUtils.createTempCameraUri(context)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("capture_photo_btn")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera")
                        }
                    }

                    draft.processedPhoto?.let { processed ->
                        Image(
                            bitmap = processed.aiBitmap.asImageBitmap(),
                            contentDescription = "Selected hazard photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    when (val ai = draft.aiState) {
                        AiClassificationState.Idle -> {
                            Text(
                                text = "Upload or capture a photo for optional Gemini vision classification, or choose a category manually below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AiClassificationState.Analyzing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Analyzing photo with Gemini AI...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is AiClassificationState.Success -> {
                            val res = ai.result
                            val suggestedCat = HazardCategoryCatalog.findById(res.categoryId)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "AI Suggestion: ${suggestedCat.label} (${(res.confidence * 100).toInt()}% confidence)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = PrimaryBlue
                                        )
                                    }
                                    Text(res.description, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "AI suggestions are not verified incidents and require human review. You may override the category below.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is AiClassificationState.Unavailable -> {
                            Surface(
                                color = RiskModerateContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ai.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RiskModerateAmber,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Hazard Category Selection (Urban & Natural Disasters)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("2. Hazard Category", style = MaterialTheme.typography.titleMedium)
                    HazardGroup.entries.forEach { group ->
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HazardCategoryCatalog.categories
                                .filter { it.group == group }
                                .forEach { cat ->
                                    FilterChip(
                                        selected = draft.categoryId == cat.id,
                                        onClick = { viewModel.updateDraft { it.copy(categoryId = cat.id) } },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = cat.icon,
                                                contentDescription = null,
                                                tint = cat.color,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(cat.label) },
                                        modifier = Modifier.testTag("cat_chip_${cat.id}")
                                    )
                                }
                        }
                    }
                }
            }

            // 3. Location (GPS + Interactive Tap-to-Adjust Map)
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
                        Text("3. Location & Pin Adjustment", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = {
                                if (LocationHelper.hasLocationPermission(context)) {
                                    permissionDeniedNote = null
                                    viewModel.fetchGpsForDeviceAndReport(context)
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            enabled = !draft.isFetchingLocation,
                            modifier = Modifier.testTag("use_gps_btn")
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (draft.isFetchingLocation) "Locating..." else "Use GPS")
                        }
                    }

                    if (!permissionDeniedNote.isNullOrBlank()) {
                        Surface(
                            color = RiskModerateContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = permissionDeniedNote.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = RiskModerateAmber,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    if (!draft.locationError.isNullOrBlank()) {
                        Text(
                            text = draft.locationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Tap anywhere on the map to adjust the hazard pin (${String.format(Locale.US, "%.4f, %.4f", draft.latitude, draft.longitude)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HazardMapCanvas(
                        centerLat = draft.latitude,
                        centerLon = draft.longitude,
                        initialZoom = region.defaultZoom + 1f,
                        incidents = emptyList(),
                        pickLocationMode = true,
                        pickedLocation = draft.latitude to draft.longitude,
                        onMapPick = { lat, lon -> viewModel.updateDraftMapLocation(context, lat, lon) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )

                    OutlinedTextField(
                        value = draft.locationLabel,
                        onValueChange = { loc -> viewModel.updateDraft { it.copy(locationLabel = loc) } },
                        label = { Text("Road / Landmark Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("report_location_input")
                    )
                }
            }

            // 4. Title, Description & Severity
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("4. Hazard Details & Severity", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { t -> viewModel.updateDraft { it.copy(title = t) } },
                        label = { Text("Brief Title (required)") },
                        placeholder = { Text("e.g., Waterlogged service road near Silk Board") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("report_title_input")
                    )

                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { d -> viewModel.updateDraft { it.copy(description = d) } },
                        label = { Text("Description (required)") },
                        placeholder = { Text("Describe depth, blocked lanes, or immediate visibility hazards...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("report_description_input")
                    )

                    Text("Indicated Severity", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HazardSeverity.entries.forEach { sev ->
                            FilterChip(
                                selected = draft.severity == sev,
                                onClick = { viewModel.updateDraft { it.copy(severity = sev) } },
                                label = { Text(sev.label) },
                                modifier = Modifier.testTag("severity_chip_${sev.name}")
                            )
                        }
                    }
                }
            }

            // Potential Duplicate Check
            val duplicates = remember(draft.latitude, draft.longitude, draft.categoryId) {
                viewModel.findDraftDuplicates()
            }
            if (duplicates.isNotEmpty()) {
                Surface(
                    color = RiskModerateContainer,
                    border = BorderStroke(1.dp, RiskModerateAmber.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = RiskModerateAmber)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Possible Duplicate Nearby (${duplicates.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = RiskModerateAmber
                            )
                            Text(
                                text = "${duplicates.first().incident.title} (${duplicates.first().distanceMeters}m away). You may still submit if this is a distinct or worsening hazard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (!draft.submitError.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = draft.submitError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 5. Preview & Submit
            if (!showPreviewStep) {
                Button(
                    onClick = { showPreviewStep = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepNavy, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("preview_report_btn")
                ) {
                    Text("Preview Hazard Report")
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, PrimaryBlue),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Report Preview", style = MaterialTheme.typography.titleMedium, color = PrimaryBlue)
                        Text("Category: ${HazardCategoryCatalog.findById(draft.categoryId).label}", fontWeight = FontWeight.SemiBold)
                        Text("Title: ${draft.title.ifBlank { "(Missing)" }}")
                        Text("Location: ${draft.locationLabel}")
                        SeverityRiskPill(severity = draft.severity, prefix = "Severity")
                        Text("Initial Status: Pending Verification", style = MaterialTheme.typography.bodySmall)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPreviewStep = false },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Edit")
                            }
                            Button(
                                onClick = { viewModel.submitHazardReport() },
                                enabled = !draft.isSubmitting,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("submit_report_btn")
                            ) {
                                if (draft.isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Submit Report")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
