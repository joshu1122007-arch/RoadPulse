package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "public",
    val preferredRegionId: String = RegionCatalog.defaultRegion.id,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPreference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val alertRadiusKm: Double = 10.0,
    val enabledCategories: List<String> = HazardCategoryCatalog.categories.map { it.id },
    val notificationFrequency: String = NotificationFrequency.REALTIME.name,
    val watchedRegions: List<String> = listOf(RegionCatalog.defaultRegion.id),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val incidentId: String = "",
    val title: String = "",
    val message: String = "",
    val categoryId: String = "other_urban",
    val approximateLocation: String = "",
    val incidentStatus: String = IncidentStatus.PENDING_VERIFICATION.name,
    val notificationType: String = "NEARBY_HAZARD",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IncidentRecord(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterRoleLabel: String = "Citizen Reporter",
    val categoryId: String = "other_urban",
    val title: String = "",
    val description: String = "",
    val latitude: Double = RegionCatalog.defaultRegion.centerLat,
    val longitude: Double = RegionCatalog.defaultRegion.centerLon,
    val locationLabel: String = "",
    val regionId: String = RegionCatalog.defaultRegion.id,
    val severity: String = HazardSeverity.MODERATE.name,
    val status: String = IncidentStatus.PENDING_VERIFICATION.name,
    val visibility: String = "PUBLIC",
    val riskLevel: String = HazardSeverity.MODERATE.name,
    val riskSummary: String = "",
    val source: String = "COMMUNITY_REPORT",
    val thumbnailBase64: String? = null,
    val photoPresent: Boolean = false,
    val aiCategory: String? = null,
    val aiConfidence: Double? = null,
    val aiDescription: String? = null,
    val aiSuggestedSeverity: String? = null,
    val aiRequiresHumanReview: Boolean? = null,
    val aiStatus: String = "SKIPPED",
    val weatherCondition: String? = null,
    val weatherTempC: Double? = null,
    val weatherRainfallMm: Double? = null,
    val weatherSource: String? = null,
    val verificationNotes: String? = null,
    val verifiedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null,
    val isDemoRecord: Boolean = false // Local-only flag, never written to Firestore
) {
    val category: HazardCategory get() = HazardCategoryCatalog.findById(categoryId)
    val severityEnum: HazardSeverity get() = HazardSeverity.fromRaw(severity)
    val statusEnum: IncidentStatus get() = IncidentStatus.fromRaw(status)
    val riskEnum: HazardSeverity get() = HazardSeverity.fromRaw(riskLevel)
}

@Keep
@IgnoreExtraProperties
data class IncidentUpdateRecord(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val reporterId: String = "",
    val visibility: String = "PUBLIC",
    val previousStatus: String = "NONE",
    val newStatus: String = IncidentStatus.PENDING_VERIFICATION.name,
    val updatedBy: String = "",
    val updatedByRole: String = "CITIZEN",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EvidenceRecord(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val reporterId: String = "",
    val visibility: String = "PUBLIC",
    val fileUrl: String = "",
    val fileType: String = "image/jpeg",
    val caption: String = "",
    val thumbnailBase64: String? = null,
    @ServerTimestamp val uploadedAt: Timestamp? = null
)

typealias Incident = IncidentRecord
typealias Evidence = EvidenceRecord
typealias IncidentUpdate = IncidentUpdateRecord


