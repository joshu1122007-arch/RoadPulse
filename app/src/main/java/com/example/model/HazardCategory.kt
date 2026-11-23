package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class HazardGroup(val label: String) {
    URBAN("Urban Road Hazard"),
    DISASTER("Natural Disaster Threat")
}

data class HazardCategory(
    val id: String,
    val label: String,
    val group: HazardGroup,
    val description: String,
    val color: Color,
    val icon: ImageVector,
    val baseRiskWeight: Int // 1 (low) to 3 (high)
)

object HazardCategoryCatalog {
    val categories: List<HazardCategory> = listOf(
        // Urban Road Hazards
        HazardCategory(
            id = "pothole",
            label = "Pothole",
            group = HazardGroup.URBAN,
            description = "Deep asphalt cavity or surface crater creating vehicle damage or swerve risks.",
            color = Color(0xFFD97706),
            icon = Icons.Filled.Warning,
            baseRiskWeight = 2
        ),
        HazardCategory(
            id = "damaged_road",
            label = "Damaged Road",
            group = HazardGroup.URBAN,
            description = "Cracked pavement, sunken carriageway, or eroded road shoulder.",
            color = Color(0xFFB45309),
            icon = Icons.Filled.BrokenImage,
            baseRiskWeight = 2
        ),
        HazardCategory(
            id = "waterlogging",
            label = "Waterlogging",
            group = HazardGroup.URBAN,
            description = "Standing stormwater pooling across lanes or underpasses.",
            color = Color(0xFF0284C7),
            icon = Icons.Filled.Water,
            baseRiskWeight = 2
        ),
        HazardCategory(
            id = "fallen_tree",
            label = "Fallen Tree",
            group = HazardGroup.URBAN,
            description = "Uprooted tree or heavy branches obstructing roadway or overhead wires.",
            color = Color(0xFF15803D),
            icon = Icons.Filled.Forest,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "road_blockage",
            label = "Road Blockage",
            group = HazardGroup.URBAN,
            description = "Debris, stalled heavy vehicle, or physical barrier blocking traffic.",
            color = Color(0xFFC62828),
            icon = Icons.Filled.DoNotDisturbOn,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "construction",
            label = "Construction Obstruction",
            group = HazardGroup.URBAN,
            description = "Unmarked trenches, barricades, or utility excavation encroaching on lanes.",
            color = Color(0xFFEA580C),
            icon = Icons.Filled.Construction,
            baseRiskWeight = 2
        ),
        HazardCategory(
            id = "damaged_infra",
            label = "Damaged Infrastructure",
            group = HazardGroup.URBAN,
            description = "Broken guardrail, exposed manhole cover, leaning pole, or failed traffic signal.",
            color = Color(0xFF7C3AED),
            icon = Icons.Filled.Build,
            baseRiskWeight = 2
        ),
        HazardCategory(
            id = "other_urban",
            label = "Other Urban Hazard",
            group = HazardGroup.URBAN,
            description = "Other localized urban roadway safety hazard.",
            color = Color(0xFF475569),
            icon = Icons.Filled.ReportProblem,
            baseRiskWeight = 1
        ),
        // Natural Disaster Hazards
        HazardCategory(
            id = "flooding",
            label = "Flooding",
            group = HazardGroup.DISASTER,
            description = "Rising floodwaters submerging roadway corridors or low-lying bridges.",
            color = Color(0xFF0369A1),
            icon = Icons.Filled.Flood,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "landslide",
            label = "Landslide",
            group = HazardGroup.DISASTER,
            description = "Slope failure, mudslide, or earth movement covering or destabilizing the road.",
            color = Color(0xFF991B1B),
            icon = Icons.Filled.Landslide,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "rockfall",
            label = "Rockfall",
            group = HazardGroup.DISASTER,
            description = "Boulders or fractured rock debris fallen from adjacent embankments.",
            color = Color(0xFF78350F),
            icon = Icons.Filled.Dangerous,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "road_collapse",
            label = "Road Collapse",
            group = HazardGroup.DISASTER,
            description = "Sinkhole, washed-out culvert, or structural pavement cave-in.",
            color = Color(0xFFDC2626),
            icon = Icons.Filled.Engineering,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "flash_flood",
            label = "Flash Flood",
            group = HazardGroup.DISASTER,
            description = "Rapidly moving stormwater surge across arterial roads or valleys.",
            color = Color(0xFF1D4ED8),
            icon = Icons.Filled.Waves,
            baseRiskWeight = 3
        ),
        HazardCategory(
            id = "severe_weather",
            label = "Severe Weather Hazard",
            group = HazardGroup.DISASTER,
            description = "High-wind debris, hail, zero-visibility squall, or lightning-downed lines.",
            color = Color(0xFF4338CA),
            icon = Icons.Filled.Thunderstorm,
            baseRiskWeight = 2
        ),
        HazardCategory(
            id = "other_disaster",
            label = "Other Disaster Threat",
            group = HazardGroup.DISASTER,
            description = "Other natural disaster-related threat impacting corridor safety.",
            color = Color(0xFF334155),
            icon = Icons.Filled.ReportProblem,
            baseRiskWeight = 2
        )
    )

    private val byId = categories.associateBy { it.id }

    fun findById(id: String): HazardCategory =
        byId[id] ?: byId.getValue("other_urban")
}

data class RegionConfig(
    val id: String,
    val name: String,
    val stateLabel: String,
    val centerLat: Double,
    val centerLon: Double,
    val defaultZoom: Float,
    val coverageRadiusKm: Double,
    val isPilotRegion: Boolean = false
)

object RegionCatalog {
    val regions: List<RegionConfig> = listOf(
        RegionConfig(
            id = "bengaluru_metro",
            name = "Bengaluru Metro",
            stateLabel = "Karnataka, India (Initial Pilot)",
            centerLat = 12.9716,
            centerLon = 77.5946,
            defaultZoom = 12.2f,
            coverageRadiusKm = 35.0,
            isPilotRegion = true
        ),
        RegionConfig(
            id = "mysuru_corridor",
            name = "Mysuru Highway Corridor",
            stateLabel = "Karnataka, India",
            centerLat = 12.2958,
            centerLon = 76.6394,
            defaultZoom = 12.0f,
            coverageRadiusKm = 30.0
        ),
        RegionConfig(
            id = "kodagu_ghats",
            name = "Kodagu & Western Ghats",
            stateLabel = "Karnataka, India (Landslide Prone)",
            centerLat = 12.4244,
            centerLon = 75.7382,
            defaultZoom = 11.5f,
            coverageRadiusKm = 40.0
        ),
        RegionConfig(
            id = "mangaluru_coast",
            name = "Coastal Mangaluru",
            stateLabel = "Karnataka, India (Monsoon Belt)",
            centerLat = 12.9141,
            centerLon = 74.8560,
            defaultZoom = 12.0f,
            coverageRadiusKm = 30.0
        )
    )

    val defaultRegion: RegionConfig = regions.first()

    fun findById(id: String): RegionConfig =
        regions.firstOrNull { it.id == id } ?: defaultRegion
}

enum class HazardSeverity(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High");

    companion object {
        fun fromRaw(raw: String?): HazardSeverity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MODERATE
    }
}

enum class IncidentStatus(val label: String) {
    PENDING_VERIFICATION("Pending Verification"),
    VERIFIED("Verified"),
    UNDER_INVESTIGATION("Under Investigation"),
    RESOLVED("Resolved"),
    REJECTED("Rejected");

    companion object {
        fun fromRaw(raw: String?): IncidentStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING_VERIFICATION
    }
}

enum class NotificationFrequency(val label: String) {
    REALTIME("Real-time"),
    HOURLY_DIGEST("Hourly Digest"),
    DAILY_SUMMARY("Daily Summary");

    companion object {
        fun fromRaw(raw: String?): NotificationFrequency =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: REALTIME
    }
}
