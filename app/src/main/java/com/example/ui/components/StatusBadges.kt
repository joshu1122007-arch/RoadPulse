package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ImageUtils
import com.example.model.HazardSeverity
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.model.RiskAssessmentResult
import com.example.model.WeatherContext
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskHighContainer
import com.example.ui.theme.RiskHighRed
import com.example.ui.theme.RiskLowContainer
import com.example.ui.theme.RiskLowGreen
import com.example.ui.theme.RiskModerateAmber
import com.example.ui.theme.RiskModerateContainer
import com.example.ui.theme.StatusPendingContainer
import com.example.ui.theme.StatusPendingGray
import com.example.ui.theme.StatusVerifiedBlue
import com.example.ui.theme.StatusVerifiedContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusPill(status: IncidentStatus, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (status) {
        IncidentStatus.PENDING_VERIFICATION -> Triple(StatusPendingContainer, StatusPendingGray, Icons.Filled.HourglassTop)
        IncidentStatus.VERIFIED -> Triple(StatusVerifiedContainer, StatusVerifiedBlue, Icons.Filled.Verified)
        IncidentStatus.UNDER_INVESTIGATION -> Triple(RiskModerateContainer, RiskModerateAmber, Icons.Filled.ManageSearch)
        IncidentStatus.RESOLVED -> Triple(RiskLowContainer, RiskLowGreen, Icons.Filled.TaskAlt)
        IncidentStatus.REJECTED -> Triple(RiskHighContainer, RiskHighRed, Icons.Filled.Cancel)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SeverityRiskPill(
    severity: HazardSeverity,
    prefix: String = "Severity",
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (severity) {
        HazardSeverity.LOW -> Triple(RiskLowContainer, RiskLowGreen, Icons.Filled.CheckCircle)
        HazardSeverity.MODERATE -> Triple(RiskModerateContainer, RiskModerateAmber, Icons.Filled.Warning)
        HazardSeverity.HIGH -> Triple(RiskHighContainer, RiskHighRed, Icons.Filled.ErrorOutline)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                text = "$prefix: ${severity.label}",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SourcePill(incident: IncidentRecord, modifier: Modifier = Modifier) {
    val (label, bg, fg, icon) = when {
        incident.isDemoRecord -> Quad("Demo Sample", Color(0xFFEDE9FE), Color(0xFF5B21B6), Icons.Filled.Science)
        incident.source == "OFFICIAL_WARNING" -> Quad("Official Warning", Color(0xFFE0E7FF), Color(0xFF1E3A8A), Icons.Filled.Shield)
        else -> Quad("Citizen Report", Color(0xFFE2E8F0), Color(0xFF334155), Icons.Filled.Public)
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun DemoEnvironmentBanner(modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFEDE9FE),
        border = BorderStroke(1.dp, Color(0xFFC4B5FD)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                tint = Color(0xFF5B21B6),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Demo Sample Overlay Active: Sample Bengaluru incidents are explicitly badged as 'Demo Sample' and separated from live Firestore reports.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4C1D95)
            )
        }
    }
}

@Composable
fun WeatherContextCard(
    weather: WeatherContext,
    isLoading: Boolean,
    regionName: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Icon(
                        imageVector = if (weather.isAvailable) Icons.Filled.WbSunny else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (weather.isAvailable) PrimaryBlue else StatusPendingGray
                    )
                    Column {
                        Text(
                            text = "Weather Context • $regionName",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (weather.isAvailable) {
                                val freshness = if (weather.isStale) "Stale" else "Current"
                                "Source: ${weather.source} • $freshness • ${formatMillis(weather.observedAtMillis)}"
                            } else {
                                "Source: Open-Meteo • Unavailable"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh weather data"
                        )
                    }
                }
            }

            if (weather.isAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WeatherMetricTile(
                        label = "Condition",
                        value = weather.conditionLabel,
                        modifier = Modifier.weight(1f)
                    )
                    WeatherMetricTile(
                        label = "Temp",
                        value = weather.temperatureC?.let { "${String.format(Locale.US, "%.1f", it)}°C" } ?: "--",
                        modifier = Modifier.weight(0.7f)
                    )
                    WeatherMetricTile(
                        label = "Rainfall",
                        value = weather.rainfallMm?.let { "${String.format(Locale.US, "%.1f", it)} mm" } ?: "0.0 mm",
                        modifier = Modifier.weight(0.8f)
                    )
                }
                if (!weather.severeWeatherWarning.isNullOrBlank()) {
                    Surface(
                        color = RiskModerateContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WaterDrop,
                                contentDescription = null,
                                tint = RiskModerateAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = weather.severeWeatherWarning,
                                style = MaterialTheme.typography.bodySmall,
                                color = RiskModerateAmber
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = weather.errorMessage ?: "Live weather observations are currently unavailable. Risk indicators exclude rainfall until weather data reconnects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun IndicativeRiskCard(
    assessment: RiskAssessmentResult,
    title: String = "Indicative Hazard Risk Assessment",
    modifier: Modifier = Modifier
) {
    val (accentColor, bgColor) = when (assessment.level) {
        HazardSeverity.LOW -> RiskLowGreen to RiskLowContainer.copy(alpha = 0.45f)
        HazardSeverity.MODERATE -> RiskModerateAmber to RiskModerateContainer.copy(alpha = 0.55f)
        HazardSeverity.HIGH -> RiskHighRed to RiskHighContainer.copy(alpha = 0.55f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                SeverityRiskPill(severity = assessment.level, prefix = "Indicative Risk")
            }

            Surface(
                color = bgColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = assessment.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Text(
                text = "Contributing rule-based factors:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            assessment.factors.forEach { factor ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = accentColor, fontWeight = FontWeight.Bold)
                    Text(
                        text = factor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = assessment.disclaimer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncidentRowCard(
    incident: IncidentRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = incident.category
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = category.color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.label,
                        tint = category.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = category.color,
                        fontWeight = FontWeight.Bold
                    )
                    SourcePill(incident = incident)
                }

                Text(
                    text = incident.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = incident.locationLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(status = incident.statusEnum)
                    SeverityRiskPill(severity = incident.severityEnum, prefix = "Severity")
                    if (incident.aiStatus == "COMPLETED" && !incident.aiCategory.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "AI Assisted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatTimestamp(incident.createdAt?.toDate()?.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IncidentThumbnailImage(
    base64: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(base64) { ImageUtils.decodeBase64Bitmap(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(14.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No Photo Attached",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyHazardState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_hazards),
                contentDescription = "No hazard reports illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatMillis(millis: Long): String {
    if (millis <= 0L) return "--"
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Just now"
    val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
