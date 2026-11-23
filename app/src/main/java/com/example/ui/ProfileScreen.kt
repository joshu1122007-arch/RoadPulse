package com.example.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.signOutUser
import com.example.model.IncidentRecord
import com.example.model.RegionCatalog
import com.example.ui.components.EmptyHazardState
import com.example.ui.components.IncidentRowCard
import com.example.ui.theme.PrimaryBlue
import com.example.viewmodel.RoadPulseViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: RoadPulseViewModel,
    onOpenIncident: (IncidentRecord) -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val myReportsState by viewModel.myReportsState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val showDemo by viewModel.showDemoData.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    var nameInput by remember(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }
    var phoneInput by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }
    var preferredRegionId by remember(profile?.preferredRegionId, selectedRegion.id) {
        mutableStateOf(profile?.preferredRegionId ?: selectedRegion.id)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. User Account & Role Card
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Person, contentDescription = null, tint = PrimaryBlue)
                                    }
                                }
                                Column {
                                    Text(
                                        text = profile?.name?.ifBlank { "RoadPulse Member" } ?: "RoadPulse Member",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = profile?.email.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = if (isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (isAdmin) "Administrator" else "Public User",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isAdmin) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Phone Number (Optional, private)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                        )

                        Text("Default Monitored Region", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RegionCatalog.regions.forEach { reg ->
                                FilterChip(
                                    selected = preferredRegionId == reg.id,
                                    onClick = { preferredRegionId = reg.id },
                                    label = { Text(reg.name) }
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.updateProfile(nameInput, phoneInput, preferredRegionId) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_profile_btn")
                        ) {
                            Text("Save Profile Settings")
                        }
                    }
                }
            }

            // 2. Demo Data Toggle & Admin Shortcut
            item {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sample Demo Dataset Overlay", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Show clearly-badged Bengaluru sample hazards alongside live Firestore reports.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showDemo,
                                onCheckedChange = { viewModel.setShowDemoData(it) },
                                modifier = Modifier.testTag("demo_toggle_switch")
                            )
                        }

                        if (isAdmin) {
                            OutlinedButton(
                                onClick = onOpenAdmin,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_admin_btn")
                            ) {
                                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Administrator Dashboard")
                            }
                        }

                        OutlinedButton(
                            onClick = { signOutUser(credentialManager, scope) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("logout_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }

            // 3. My Submitted Reports & Status Tracking
            item {
                Text("My Submitted Reports", style = MaterialTheme.typography.titleMedium)
            }

            when (val myState = myReportsState) {
                is UiState.Loading -> {
                    item { CircularProgressIndicator() }
                }
                is UiState.Error -> {
                    item { Text(myState.message, color = MaterialTheme.colorScheme.error) }
                }
                is UiState.Success -> {
                    if (myState.data.isEmpty()) {
                        item {
                            EmptyHazardState(
                                title = "No Reports Submitted Yet",
                                message = "Any hazard reports you submit will appear here so you can track verification status updates."
                            )
                        }
                    } else {
                        items(myState.data, key = { it.id }) { report ->
                            IncidentRowCard(
                                incident = report,
                                onClick = { onOpenIncident(report) }
                            )
                        }
                    }
                }
            }
        }
    }
}
