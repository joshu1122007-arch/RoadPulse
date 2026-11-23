package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.EvidenceRecord
import com.example.model.HazardSeverity
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.model.IncidentUpdateRecord
import com.example.model.IndicativeRiskEngine
import com.example.model.WeatherContext
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

data class DuplicateCandidate(
    val incident: IncidentRecord,
    val distanceMeters: Int
)

/**
 * Encapsulates all Firestore CRUD operations and realtime streams for the
 * `incidents`, `evidence`, and `incident_updates` collections.
 */
class HazardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // =========================================================================
    // INCIDENTS COLLECTION ('incidents') — CRUD & Realtime Streams
    // =========================================================================

    /** Aligned with allow list: resource.data.visibility == 'PUBLIC'. */
    fun observePublicIncidents(): Flow<List<IncidentRecord>> =
        db.collection(COLLECTION_INCIDENTS)
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(IncidentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "$COLLECTION_INCIDENTS?visibility=PUBLIC")
                }
                throw error
            }

    /** Aligned with allow list: resource.data.reporterId == request.auth.uid. */
    fun observeMyReports(uid: String): Flow<List<IncidentRecord>> =
        db.collection(COLLECTION_INCIDENTS)
            .whereEqualTo("reporterId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(IncidentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "$COLLECTION_INCIDENTS?reporterId=$uid")
                }
                throw error
            }

    /** Admin queue stream: unfiltered when bootstrapped admin email, otherwise scoped to PUBLIC. */
    fun observeAllIncidentsForAdmin(isAdminEmail: Boolean): Flow<List<IncidentRecord>> {
        val query = if (isAdminEmail) {
            db.collection(COLLECTION_INCIDENTS)
        } else {
            db.collection(COLLECTION_INCIDENTS).whereEqualTo("visibility", "PUBLIC")
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(IncidentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, COLLECTION_INCIDENTS)
                }
                throw error
            }
    }

    suspend fun getIncidentById(incidentId: String): Result<IncidentRecord?> {
        val docPath = "$COLLECTION_INCIDENTS/$incidentId"
        return try {
            requireUserId()
            require(incidentId.isNotBlank()) { "Incident ID cannot be blank." }
            val snapshot = db.collection(COLLECTION_INCIDENTS).document(incidentId).get().await()
            val record = if (snapshot.exists()) {
                snapshot.toObject(IncidentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    fun observeIncident(incidentId: String): Flow<IncidentRecord?> =
        db.collection(COLLECTION_INCIDENTS).document(incidentId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(IncidentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "$COLLECTION_INCIDENTS/$incidentId")
                }
                throw error
            }

    suspend fun submitIncident(
        categoryId: String,
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        regionId: String,
        severity: HazardSeverity,
        thumbnailBase64: String?,
        aiState: AiClassificationState,
        weather: WeatherContext?
    ): Result<String> {
        var docPath = COLLECTION_INCIDENTS
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(1000)
            val cleanLoc = locationLabel.trim().take(160)
            require(cleanTitle.length >= 3) { "Title must be at least 3 characters." }
            require(cleanDesc.length >= 5) { "Description must be at least 5 characters." }
            require(cleanLoc.length >= 2) { "Location label must be at least 2 characters." }

            val risk = IndicativeRiskEngine.assessIncident(
                categoryId = categoryId,
                severity = severity,
                status = IncidentStatus.PENDING_VERIFICATION,
                createdAt = null,
                weather = weather
            )

            val aiResult = (aiState as? AiClassificationState.Success)?.result
            val aiStatus = when (aiState) {
                is AiClassificationState.Success -> "COMPLETED"
                is AiClassificationState.Unavailable -> "UNAVAILABLE"
                else -> "SKIPPED"
            }

            val docRef = db.collection(COLLECTION_INCIDENTS).document()
            docPath = docRef.path

            val payload = mapOf(
                "reporterId" to uid,
                "reporterRoleLabel" to "Citizen Reporter",
                "categoryId" to categoryId.take(64),
                "title" to cleanTitle,
                "description" to cleanDesc,
                "latitude" to latitude.coerceIn(-90.0, 90.0),
                "longitude" to longitude.coerceIn(-180.0, 180.0),
                "locationLabel" to cleanLoc,
                "regionId" to regionId.take(64),
                "severity" to severity.name,
                "status" to IncidentStatus.PENDING_VERIFICATION.name,
                "visibility" to "PUBLIC",
                "riskLevel" to risk.level.name,
                "riskSummary" to risk.summary.take(400),
                "source" to "COMMUNITY_REPORT",
                "thumbnailBase64" to thumbnailBase64?.takeIf { it.isNotBlank() && it.length <= 260_000 },
                "photoPresent" to !thumbnailBase64.isNullOrBlank(),
                "aiCategory" to aiResult?.categoryId?.take(64),
                "aiConfidence" to aiResult?.confidence,
                "aiDescription" to aiResult?.description?.take(400),
                "aiSuggestedSeverity" to aiResult?.suggestedSeverity?.name,
                "aiRequiresHumanReview" to aiResult?.requiresHumanReview,
                "aiStatus" to aiStatus,
                "weatherCondition" to weather?.takeIf { it.isAvailable }?.conditionLabel?.take(80),
                "weatherTempC" to weather?.takeIf { it.isAvailable }?.temperatureC,
                "weatherRainfallMm" to weather?.takeIf { it.isAvailable }?.rainfallMm,
                "weatherSource" to weather?.takeIf { it.isAvailable }?.source?.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()

            val updateRef = db.collection(COLLECTION_INCIDENT_UPDATES).document()
            val auditPayload = mapOf(
                "incidentId" to docRef.id,
                "reporterId" to uid,
                "visibility" to "PUBLIC",
                "previousStatus" to "NONE",
                "newStatus" to IncidentStatus.PENDING_VERIFICATION.name,
                "updatedBy" to uid,
                "updatedByRole" to "CITIZEN",
                "notes" to "Submitted by citizen reporter for verification.",
                "createdAt" to FieldValue.serverTimestamp()
            )
            updateRef.set(auditPayload).await()

            val validThumb = thumbnailBase64?.takeIf { it.isNotBlank() && it.length <= 260_000 }
            if (validThumb != null) {
                val evidenceRef = db.collection(COLLECTION_EVIDENCE).document()
                val evidencePayload = mapOf(
                    "incidentId" to docRef.id,
                    "reporterId" to uid,
                    "visibility" to "PUBLIC",
                    "fileType" to "image/jpeg",
                    "caption" to "Initial hazard photograph",
                    "thumbnailBase64" to validThumb,
                    "uploadedAt" to FieldValue.serverTimestamp()
                )
                evidenceRef.set(evidencePayload).await()
            }

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /** Citizen owner update while incident remains PENDING_VERIFICATION. */
    suspend fun updateIncidentDetails(
        incident: IncidentRecord,
        categoryId: String,
        title: String,
        description: String,
        severity: HazardSeverity
    ): Result<Unit> {
        val docPath = "$COLLECTION_INCIDENTS/${incident.id}"
        return try {
            requireUserId()
            require(incident.id.isNotBlank()) { "Incident ID missing." }
            require(!incident.isDemoRecord) { "Demo sample records cannot be modified in Firestore." }

            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(1000)
            require(cleanTitle.length >= 3) { "Title must be at least 3 characters." }
            require(cleanDesc.length >= 5) { "Description must be at least 5 characters." }

            val updatedRisk = IndicativeRiskEngine.assessIncident(
                categoryId = categoryId,
                severity = severity,
                status = incident.statusEnum,
                createdAt = incident.createdAt,
                weather = null
            )

            val updateMap = mapOf(
                "categoryId" to categoryId.take(64),
                "title" to cleanTitle,
                "description" to cleanDesc,
                "severity" to severity.name,
                "riskLevel" to updatedRisk.level.name,
                "riskSummary" to updatedRisk.summary.take(400),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection(COLLECTION_INCIDENTS).document(incident.id).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /** Admin status transition + audit log creation + visibility synchronization. */
    suspend fun updateIncidentStatus(
        incident: IncidentRecord,
        newStatus: IncidentStatus,
        notes: String
    ): Result<Unit> {
        var docPath = "$COLLECTION_INCIDENTS/${incident.id}"
        return try {
            val adminUid = requireUserId()
            require(incident.id.isNotBlank()) { "Incident ID missing." }
            require(!incident.isDemoRecord) { "Demo sample records cannot be modified in Firestore." }

            val docRef = db.collection(COLLECTION_INCIDENTS).document(incident.id)
            docPath = docRef.path

            val newVisibility = if (newStatus == IncidentStatus.REJECTED) "REPORTER_ONLY" else "PUBLIC"
            val updatedRisk = IndicativeRiskEngine.assessIncident(
                categoryId = incident.categoryId,
                severity = incident.severityEnum,
                status = newStatus,
                createdAt = incident.createdAt,
                weather = null
            )

            val updateMap = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "visibility" to newVisibility,
                "riskLevel" to updatedRisk.level.name,
                "riskSummary" to updatedRisk.summary.take(400),
                "verificationNotes" to notes.trim().take(500),
                "verifiedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus == IncidentStatus.RESOLVED) {
                updateMap["resolvedAt"] = FieldValue.serverTimestamp()
            }

            docRef.update(updateMap).await()

            val auditRef = db.collection(COLLECTION_INCIDENT_UPDATES).document()
            val auditPayload = mapOf(
                "incidentId" to incident.id,
                "reporterId" to incident.reporterId,
                "visibility" to newVisibility,
                "previousStatus" to incident.status,
                "newStatus" to newStatus.name,
                "updatedBy" to adminUid,
                "updatedByRole" to "ADMIN",
                "notes" to notes.trim().ifBlank { "Status updated to ${newStatus.label}" }.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
            auditRef.set(auditPayload).await()

            if (incident.visibility != newVisibility) {
                val evidenceSnap = db.collection(COLLECTION_EVIDENCE)
                    .whereEqualTo("incidentId", incident.id)
                    .whereEqualTo("reporterId", incident.reporterId)
                    .get()
                    .await()
                for (evDoc in evidenceSnap.documents) {
                    evDoc.reference.update("visibility", newVisibility).await()
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /** Deletes related evidence (and updates if admin) before deleting the incident document. */
    suspend fun deleteIncident(
        incidentId: String,
        isAdminEmail: Boolean = false
    ): Result<Unit> {
        val docPath = "$COLLECTION_INCIDENTS/$incidentId"
        return try {
            val uid = requireUserId()
            require(incidentId.isNotBlank()) { "Incident ID missing." }

            val evidenceQuery = if (isAdminEmail) {
                db.collection(COLLECTION_EVIDENCE).whereEqualTo("incidentId", incidentId)
            } else {
                db.collection(COLLECTION_EVIDENCE)
                    .whereEqualTo("incidentId", incidentId)
                    .whereEqualTo("reporterId", uid)
            }
            for (evDoc in evidenceQuery.get().await().documents) {
                evDoc.reference.delete().await()
            }

            if (isAdminEmail) {
                val updatesQuery = db.collection(COLLECTION_INCIDENT_UPDATES)
                    .whereEqualTo("incidentId", incidentId)
                for (updDoc in updatesQuery.get().await().documents) {
                    updDoc.reference.delete().await()
                }
            }

            db.collection(COLLECTION_INCIDENTS).document(incidentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // =========================================================================
    // EVIDENCE COLLECTION ('evidence') — CRUD & Realtime Streams
    // =========================================================================

    fun observeIncidentEvidence(
        incidentId: String,
        isPublic: Boolean,
        reporterId: String,
        isAdminEmail: Boolean
    ): Flow<List<EvidenceRecord>> {
        val base = db.collection(COLLECTION_EVIDENCE).whereEqualTo("incidentId", incidentId)
        val query = when {
            isAdminEmail -> base
            isPublic -> base.whereEqualTo("visibility", "PUBLIC")
            else -> base.whereEqualTo("reporterId", reporterId)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(EvidenceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.uploadedAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "$COLLECTION_EVIDENCE?incidentId=$incidentId")
                }
                throw error
            }
    }

    suspend fun getEvidenceById(evidenceId: String): Result<EvidenceRecord?> {
        val docPath = "$COLLECTION_EVIDENCE/$evidenceId"
        return try {
            requireUserId()
            require(evidenceId.isNotBlank()) { "Evidence ID cannot be blank." }
            val snapshot = db.collection(COLLECTION_EVIDENCE).document(evidenceId).get().await()
            val record = if (snapshot.exists()) {
                snapshot.toObject(EvidenceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun addEvidence(
        incidentId: String,
        fileUrl: String = "",
        fileType: String = "image/jpeg",
        caption: String = "",
        thumbnailBase64: String? = null
    ): Result<String> {
        var docPath = COLLECTION_EVIDENCE
        return try {
            val uid = requireUserId()
            require(incidentId.isNotBlank()) { "Incident ID missing." }
            val allowedTypes = setOf("image/jpeg", "image/png", "image/webp", "application/pdf", "video/mp4")
            require(fileType in allowedTypes) { "Unsupported evidence file type." }

            val parentSnap = db.collection(COLLECTION_INCIDENTS).document(incidentId).get().await()
            require(parentSnap.exists()) { "Parent incident not found." }
            val parentReporterId = parentSnap.getString("reporterId").orEmpty()
            val parentVisibility = parentSnap.getString("visibility") ?: "PUBLIC"

            val evidenceRef = db.collection(COLLECTION_EVIDENCE).document()
            docPath = evidenceRef.path

            val payload = mapOf(
                "incidentId" to incidentId,
                "reporterId" to (parentReporterId.ifBlank { uid }),
                "visibility" to parentVisibility,
                "fileUrl" to fileUrl.trim().take(500).takeIf { it.isNotEmpty() },
                "fileType" to fileType,
                "caption" to caption.trim().take(300).takeIf { it.isNotEmpty() },
                "thumbnailBase64" to thumbnailBase64?.takeIf { it.isNotBlank() && it.length <= 260_000 },
                "uploadedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            evidenceRef.set(payload).await()
            Result.success(evidenceRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEvidenceCaption(
        evidenceId: String,
        caption: String
    ): Result<Unit> {
        val docPath = "$COLLECTION_EVIDENCE/$evidenceId"
        return try {
            requireUserId()
            require(evidenceId.isNotBlank()) { "Evidence ID missing." }
            db.collection(COLLECTION_EVIDENCE)
                .document(evidenceId)
                .update("caption", caption.trim().take(300))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEvidence(evidenceId: String): Result<Unit> {
        val docPath = "$COLLECTION_EVIDENCE/$evidenceId"
        return try {
            requireUserId()
            require(evidenceId.isNotBlank()) { "Evidence ID missing." }
            db.collection(COLLECTION_EVIDENCE)
                .document(evidenceId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // =========================================================================
    // INCIDENT UPDATES COLLECTION ('incident_updates') — CRUD & Realtime Streams
    // =========================================================================

    fun observeIncidentUpdates(
        incidentId: String,
        isPublic: Boolean,
        reporterId: String,
        isAdminEmail: Boolean
    ): Flow<List<IncidentUpdateRecord>> {
        val base = db.collection(COLLECTION_INCIDENT_UPDATES).whereEqualTo("incidentId", incidentId)
        val query = when {
            isAdminEmail -> base
            isPublic -> base.whereEqualTo("visibility", "PUBLIC")
            else -> base.whereEqualTo("reporterId", reporterId)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(IncidentUpdateRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "$COLLECTION_INCIDENT_UPDATES?incidentId=$incidentId")
                }
                throw error
            }
    }

    suspend fun getIncidentUpdateById(updateId: String): Result<IncidentUpdateRecord?> {
        val docPath = "$COLLECTION_INCIDENT_UPDATES/$updateId"
        return try {
            requireUserId()
            require(updateId.isNotBlank()) { "Update ID cannot be blank." }
            val snapshot = db.collection(COLLECTION_INCIDENT_UPDATES).document(updateId).get().await()
            val record = if (snapshot.exists()) {
                snapshot.toObject(IncidentUpdateRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun addIncidentUpdate(
        incidentId: String,
        reporterId: String,
        visibility: String,
        previousStatus: String,
        newStatus: IncidentStatus,
        updatedByRole: String,
        notes: String
    ): Result<String> {
        var docPath = COLLECTION_INCIDENT_UPDATES
        return try {
            val uid = requireUserId()
            require(incidentId.isNotBlank()) { "Incident ID missing." }
            require(updatedByRole in setOf("CITIZEN", "ADMIN")) { "Invalid role." }
            val docRef = db.collection(COLLECTION_INCIDENT_UPDATES).document()
            docPath = docRef.path
            val payload = mapOf(
                "incidentId" to incidentId,
                "reporterId" to reporterId.ifBlank { uid },
                "visibility" to (if (visibility == "REPORTER_ONLY") "REPORTER_ONLY" else "PUBLIC"),
                "previousStatus" to previousStatus,
                "newStatus" to newStatus.name,
                "updatedBy" to uid,
                "updatedByRole" to updatedByRole,
                "notes" to notes.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /** Admin-only audit note correction; status transitions & timestamps remain immutable. */
    suspend fun updateIncidentUpdateNotes(updateId: String, notes: String): Result<Unit> {
        val docPath = "$COLLECTION_INCIDENT_UPDATES/$updateId"
        return try {
            requireUserId()
            require(updateId.isNotBlank()) { "Update ID missing." }
            db.collection(COLLECTION_INCIDENT_UPDATES)
                .document(updateId)
                .update("notes", notes.trim().take(500))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /** Admin-only cleanup of an incident_updates record. */
    suspend fun deleteIncidentUpdate(updateId: String): Result<Unit> {
        val docPath = "$COLLECTION_INCIDENT_UPDATES/$updateId"
        return try {
            requireUserId()
            require(updateId.isNotBlank()) { "Update ID missing." }
            db.collection(COLLECTION_INCIDENT_UPDATES)
                .document(updateId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // =========================================================================
    // GEOSPATIAL & EXPORT HELPERS
    // =========================================================================

    fun findPotentialDuplicates(
        latitude: Double,
        longitude: Double,
        categoryId: String,
        existingIncidents: List<IncidentRecord>,
        excludeId: String? = null
    ): List<DuplicateCandidate> {
        val maxWindowMs = 48L * 3600L * 1000L
        val now = System.currentTimeMillis()
        return existingIncidents.mapNotNull { candidate ->
            if (candidate.id == excludeId) return@mapNotNull null
            if (candidate.statusEnum == IncidentStatus.REJECTED) return@mapNotNull null
            if (candidate.categoryId != categoryId) return@mapNotNull null
            val ageMs = candidate.createdAt?.let { now - it.toDate().time } ?: 0L
            if (ageMs > maxWindowMs) return@mapNotNull null
            val distMeters = (LocationHelper.haversineKm(
                latitude, longitude, candidate.latitude, candidate.longitude
            ) * 1000.0).toInt()
            if (distMeters <= 300) DuplicateCandidate(candidate, distMeters) else null
        }.sortedBy { it.distanceMeters }
    }

    fun buildCsvExport(incidents: List<IncidentRecord>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val header = "id,category,title,status,severity,risk_level,latitude,longitude,location_label,region_id,source,ai_status,ai_category,ai_confidence,created_at,is_demo"
        val rows = incidents.map { inc ->
            listOf(
                inc.id,
                inc.categoryId,
                inc.title,
                inc.status,
                inc.severity,
                inc.riskLevel,
                inc.latitude.toString(),
                inc.longitude.toString(),
                inc.locationLabel,
                inc.regionId,
                inc.source,
                inc.aiStatus,
                inc.aiCategory.orEmpty(),
                inc.aiConfidence?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                inc.createdAt?.toDate()?.let { sdf.format(it) }.orEmpty(),
                inc.isDemoRecord.toString()
            ).joinToString(",") { cell ->
                "\"${cell.replace("\"", "\"\"")}\""
            }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    companion object {
        const val COLLECTION_INCIDENTS = "incidents"
        const val COLLECTION_EVIDENCE = "evidence"
        const val COLLECTION_INCIDENT_UPDATES = "incident_updates"
    }
}
