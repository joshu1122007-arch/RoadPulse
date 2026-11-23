package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiClassificationState
import com.example.data.DuplicateCandidate
import com.example.data.HazardAiRepository
import com.example.data.HazardRepository
import com.example.data.ImageUtils
import com.example.data.LocalPreferencesDataStore
import com.example.data.LocationHelper
import com.example.data.ProcessedPhoto
import com.example.data.UserRepository
import com.example.data.WeatherRepository
import com.example.model.DemoDataCatalog
import com.example.model.EvidenceRecord
import com.example.model.HazardCategoryCatalog
import com.example.model.HazardSeverity
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.model.IncidentUpdateRecord
import com.example.model.NotificationFrequency
import com.example.model.RegionCatalog
import com.example.model.RegionConfig
import com.example.model.UserNotification
import com.example.model.UserPreference
import com.example.model.UserProfile
import com.example.model.WeatherContext
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class DateFilterWindow(val label: String, val hours: Long?) {
    ALL("All Time", null),
    LAST_24H("24 Hours", 24L),
    LAST_7D("7 Days", 168L)
}

data class MapFilterState(
    val selectedCategoryId: String? = null,
    val selectedStatus: IncidentStatus? = null,
    val selectedSeverity: HazardSeverity? = null,
    val dateWindow: DateFilterWindow = DateFilterWindow.ALL,
    val searchQuery: String = ""
)

data class ReportDraftState(
    val categoryId: String = "pothole",
    val title: String = "",
    val description: String = "",
    val severity: HazardSeverity = HazardSeverity.MODERATE,
    val latitude: Double = RegionCatalog.defaultRegion.centerLat,
    val longitude: Double = RegionCatalog.defaultRegion.centerLon,
    val locationLabel: String = "MG Road, Bengaluru",
    val selectedPhotoUri: Uri? = null,
    val processedPhoto: ProcessedPhoto? = null,
    val aiState: AiClassificationState = AiClassificationState.Idle,
    val isFetchingLocation: Boolean = false,
    val locationError: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submittedIncidentId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoadPulseViewModel(
    private val currentUserId: String,
    private val hazardRepo: HazardRepository,
    private val userRepo: UserRepository,
    private val weatherRepo: WeatherRepository,
    private val aiRepo: HazardAiRepository,
    private val localPrefs: LocalPreferencesDataStore
) : ViewModel() {

    val activeUserId: String get() = currentUserId

    private val _isAdmin = MutableStateFlow(userRepo.isBootstrappedAdminEmail())
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val showDemoData: StateFlow<Boolean> = localPrefs.showDemoData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val selectedRegion: StateFlow<RegionConfig> = localPrefs.selectedRegionId
        .map { RegionCatalog.findById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), RegionCatalog.defaultRegion)

    private val _weatherState = MutableStateFlow(WeatherContext())
    val weatherState: StateFlow<WeatherContext> = _weatherState.asStateFlow()

    private val _isWeatherLoading = MutableStateFlow(false)
    val isWeatherLoading: StateFlow<Boolean> = _isWeatherLoading.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _mapFilters = MutableStateFlow(MapFilterState())
    val mapFilters: StateFlow<MapFilterState> = _mapFilters.asStateFlow()

    private val _reportDraft = MutableStateFlow(ReportDraftState())
    val reportDraft: StateFlow<ReportDraftState> = _reportDraft.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        userRepo.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load profile"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val userPreferences: StateFlow<UserPreference> =
        userRepo.observePreferences(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Preference listener fallback", firestoreError ?: error)
                emit(UserPreference(userId = currentUserId))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreference(userId = currentUserId))

    val notificationsState: StateFlow<UiState<List<UserNotification>>> =
        userRepo.observeNotifications(currentUserId)
            .map<List<UserNotification>, UiState<List<UserNotification>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load notifications"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val livePublicIncidentsState: StateFlow<UiState<List<IncidentRecord>>> =
        hazardRepo.observePublicIncidents()
            .map<List<IncidentRecord>, UiState<List<IncidentRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load hazard reports"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myReportsState: StateFlow<UiState<List<IncidentRecord>>> =
        hazardRepo.observeMyReports(currentUserId)
            .map<List<IncidentRecord>, UiState<List<IncidentRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load your reports"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminIncidentsState: StateFlow<UiState<List<IncidentRecord>>> =
        _isAdmin.flatMapLatest { admin ->
            if (!admin) {
                flowOf(UiState.Success(emptyList()))
            } else {
                hazardRepo.observeAllIncidentsForAdmin(userRepo.isBootstrappedAdminEmail())
                    .map<List<IncidentRecord>, UiState<List<IncidentRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load admin queue"))
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    // Combined public incidents + optional clearly-marked demo records
    val combinedIncidentsState: StateFlow<UiState<List<IncidentRecord>>> =
        combine(livePublicIncidentsState, showDemoData) { liveState, demoEnabled ->
            when (liveState) {
                is UiState.Loading -> if (demoEnabled) UiState.Success(DemoDataCatalog.sampleIncidents()) else UiState.Loading
                is UiState.Error -> liveState
                is UiState.Success -> {
                    val merged = if (demoEnabled) {
                        liveState.data + DemoDataCatalog.sampleIncidents()
                    } else {
                        liveState.data
                    }
                    UiState.Success(merged)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val filteredMapIncidents: StateFlow<List<IncidentRecord>> =
        combine(combinedIncidentsState, _mapFilters) { incState, filters ->
            val all = (incState as? UiState.Success)?.data.orEmpty()
            val now = System.currentTimeMillis()
            all.filter { inc ->
                val catMatch = filters.selectedCategoryId == null || inc.categoryId == filters.selectedCategoryId
                val statusMatch = filters.selectedStatus == null || inc.statusEnum == filters.selectedStatus
                val sevMatch = filters.selectedSeverity == null || inc.severityEnum == filters.selectedSeverity
                val dateMatch = filters.dateWindow.hours?.let { hrs ->
                    val ts = inc.createdAt?.toDate()?.time ?: now
                    (now - ts) <= hrs * 3600_000L
                } ?: true
                val query = filters.searchQuery.trim()
                val queryMatch = query.isEmpty() ||
                    inc.title.contains(query, ignoreCase = true) ||
                    inc.locationLabel.contains(query, ignoreCase = true) ||
                    inc.category.label.contains(query, ignoreCase = true)
                catMatch && statusMatch && sevMatch && dateMatch && queryMatch
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            userRepo.ensureUserProfileInitialized()
            _isAdmin.value = userRepo.checkIsAdmin(currentUserId)
        }
        viewModelScope.launch {
            selectedRegion.collect { region ->
                refreshWeather(region.centerLat, region.centerLon)
            }
        }
    }

    fun refreshWeather(lat: Double? = null, lon: Double? = null) {
        val targetLat = lat ?: _userLocation.value?.first ?: selectedRegion.value.centerLat
        val targetLon = lon ?: _userLocation.value?.second ?: selectedRegion.value.centerLon
        viewModelScope.launch {
            _isWeatherLoading.value = true
            val weather = weatherRepo.fetchWeather(targetLat, targetLon)
            _weatherState.value = weather
            _isWeatherLoading.value = false
            if (weather.isAvailable && !weather.severeWeatherWarning.isNullOrBlank()) {
                // Record an in-app weather alert once per session if severe warning detected
                maybeCreateSevereWeatherNotification(weather)
            }
        }
    }

    private var lastWeatherAlertText: String? = null
    private suspend fun maybeCreateSevereWeatherNotification(weather: WeatherContext) {
        val warning = weather.severeWeatherWarning ?: return
        if (warning == lastWeatherAlertText) return
        lastWeatherAlertText = warning
        userRepo.createNotificationForUser(
            targetUserId = currentUserId,
            incidentId = null,
            title = "Weather Advisory (${selectedRegion.value.name})",
            message = warning,
            categoryId = "severe_weather",
            approximateLocation = selectedRegion.value.name,
            incidentStatus = "WEATHER_ALERT",
            notificationType = "WEATHER_ALERT"
        )
    }

    fun setShowDemoData(enabled: Boolean) {
        viewModelScope.launch { localPrefs.setShowDemoData(enabled) }
    }

    fun selectRegion(regionId: String) {
        viewModelScope.launch {
            localPrefs.setSelectedRegionId(regionId)
            val region = RegionCatalog.findById(regionId)
            _reportDraft.value = _reportDraft.value.copy(
                latitude = region.centerLat,
                longitude = region.centerLon,
                locationLabel = region.name
            )
        }
    }

    fun updateMapFilters(transform: (MapFilterState) -> MapFilterState) {
        _mapFilters.value = transform(_mapFilters.value)
    }

    fun updateUserCoordinates(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
        refreshWeather(lat, lon)
    }

    fun fetchGpsForDeviceAndReport(context: Context) {
        viewModelScope.launch {
            _reportDraft.value = _reportDraft.value.copy(isFetchingLocation = true, locationError = null)
            val coords = LocationHelper.fetchCurrentLocation(context)
            if (coords != null) {
                _userLocation.value = coords.latitude to coords.longitude
                _reportDraft.value = _reportDraft.value.copy(
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    locationLabel = coords.label,
                    isFetchingLocation = false
                )
                refreshWeather(coords.latitude, coords.longitude)
            } else {
                _reportDraft.value = _reportDraft.value.copy(
                    isFetchingLocation = false,
                    locationError = "Unable to retrieve GPS fix. Adjust the pin manually on the map."
                )
            }
        }
    }

    fun onPhotoSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _reportDraft.value = _reportDraft.value.copy(
                selectedPhotoUri = uri,
                aiState = AiClassificationState.Analyzing,
                submitError = null
            )
            val processed = ImageUtils.processSelectedPhoto(context, uri)
            if (processed == null) {
                _reportDraft.value = _reportDraft.value.copy(
                    processedPhoto = null,
                    aiState = AiClassificationState.Unavailable("Failed to decode selected image. Please choose another photo.")
                )
                return@launch
            }
            _reportDraft.value = _reportDraft.value.copy(processedPhoto = processed)

            val aiResult = aiRepo.classifyHazardPhoto(processed.aiBitmap)
            val currentDraft = _reportDraft.value
            if (aiResult is AiClassificationState.Success) {
                val res = aiResult.result
                _reportDraft.value = currentDraft.copy(
                    aiState = aiResult,
                    categoryId = res.categoryId,
                    severity = res.suggestedSeverity,
                    description = if (currentDraft.description.isBlank()) res.description else currentDraft.description,
                    title = if (currentDraft.title.isBlank()) {
                        "${HazardCategoryCatalog.findById(res.categoryId).label} reported"
                    } else currentDraft.title
                )
            } else {
                _reportDraft.value = currentDraft.copy(aiState = aiResult)
            }
        }
    }

    fun updateDraft(transform: (ReportDraftState) -> ReportDraftState) {
        _reportDraft.value = transform(_reportDraft.value)
    }

    fun updateDraftMapLocation(context: Context, lat: Double, lon: Double) {
        _reportDraft.value = _reportDraft.value.copy(latitude = lat, longitude = lon)
        viewModelScope.launch {
            val label = LocationHelper.reverseGeocodeLabel(context, lat, lon)
            _reportDraft.value = _reportDraft.value.copy(locationLabel = label)
        }
    }

    fun findDraftDuplicates(): List<DuplicateCandidate> {
        val all = (combinedIncidentsState.value as? UiState.Success)?.data.orEmpty()
        val draft = _reportDraft.value
        return hazardRepo.findPotentialDuplicates(
            latitude = draft.latitude,
            longitude = draft.longitude,
            categoryId = draft.categoryId,
            existingIncidents = all
        )
    }

    fun findDuplicatesForIncident(incident: IncidentRecord): List<DuplicateCandidate> {
        val all = (adminIncidentsState.value as? UiState.Success)?.data
            ?: (combinedIncidentsState.value as? UiState.Success)?.data.orEmpty()
        return hazardRepo.findPotentialDuplicates(
            latitude = incident.latitude,
            longitude = incident.longitude,
            categoryId = incident.categoryId,
            existingIncidents = all,
            excludeId = incident.id
        )
    }

    fun submitHazardReport() {
        val draft = _reportDraft.value
        if (draft.title.trim().length < 3) {
            _reportDraft.value = draft.copy(submitError = "Please enter a brief hazard title (at least 3 characters).")
            return
        }
        if (draft.description.trim().length < 5) {
            _reportDraft.value = draft.copy(submitError = "Please provide a description (at least 5 characters).")
            return
        }
        if (draft.locationLabel.trim().length < 2) {
            _reportDraft.value = draft.copy(submitError = "Please specify or pick a valid location.")
            return
        }
        viewModelScope.launch {
            _reportDraft.value = draft.copy(isSubmitting = true, submitError = null)
            val result = hazardRepo.submitIncident(
                categoryId = draft.categoryId,
                title = draft.title,
                description = draft.description,
                latitude = draft.latitude,
                longitude = draft.longitude,
                locationLabel = draft.locationLabel,
                regionId = selectedRegion.value.id,
                severity = draft.severity,
                thumbnailBase64 = draft.processedPhoto?.thumbnailBase64,
                aiState = draft.aiState,
                weather = _weatherState.value
            )
            result.fold(
                onSuccess = { incidentId ->
                    _reportDraft.value = _reportDraft.value.copy(
                        isSubmitting = false,
                        submittedIncidentId = incidentId
                    )
                    userRepo.createNotificationForUser(
                        targetUserId = currentUserId,
                        incidentId = incidentId,
                        title = "Report Submitted: ${HazardCategoryCatalog.findById(draft.categoryId).label}",
                        message = "Your report at ${draft.locationLabel} is queued as Pending Verification.",
                        categoryId = draft.categoryId,
                        approximateLocation = draft.locationLabel,
                        incidentStatus = IncidentStatus.PENDING_VERIFICATION.name,
                        notificationType = "STATUS_UPDATE"
                    )
                },
                onFailure = { err ->
                    _reportDraft.value = _reportDraft.value.copy(
                        isSubmitting = false,
                        submitError = err.localizedMessage ?: "Failed to submit report. Please retry."
                    )
                }
            )
        }
    }

    fun resetReportDraft() {
        val region = selectedRegion.value
        val coords = _userLocation.value
        _reportDraft.value = ReportDraftState(
            latitude = coords?.first ?: region.centerLat,
            longitude = coords?.second ?: region.centerLon,
            locationLabel = if (coords != null) "Current GPS Location" else region.name
        )
    }

    fun observeIncident(incident: IncidentRecord) =
        if (incident.isDemoRecord) {
            flowOf<IncidentRecord?>(incident)
        } else {
            hazardRepo.observeIncident(incident.id)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(incident)
                }
        }

    fun observeIncidentUpdates(incident: IncidentRecord) =
        if (incident.isDemoRecord) {
            flowOf(
                listOf(
                    IncidentUpdateRecord(
                        id = "demo_upd_1",
                        incidentId = incident.id,
                        previousStatus = "NONE",
                        newStatus = incident.status,
                        updatedBy = "Demo System",
                        updatedByRole = "ADMIN",
                        notes = incident.verificationNotes ?: "Sample demo status record.",
                        createdAt = incident.updatedAt ?: incident.createdAt
                    )
                )
            )
        } else {
            hazardRepo.observeIncidentUpdates(
                incidentId = incident.id,
                isPublic = incident.visibility == "PUBLIC",
                reporterId = incident.reporterId,
                isAdminEmail = userRepo.isBootstrappedAdminEmail()
            ).catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
        }

    fun observeIncidentEvidence(incident: IncidentRecord) =
        if (incident.isDemoRecord) {
            val demoItems = if (!incident.thumbnailBase64.isNullOrBlank()) {
                listOf(
                    EvidenceRecord(
                        id = "demo_ev_1",
                        incidentId = incident.id,
                        reporterId = incident.reporterId,
                        visibility = incident.visibility,
                        fileType = "image/jpeg",
                        caption = "Sample demonstration hazard photograph",
                        thumbnailBase64 = incident.thumbnailBase64,
                        uploadedAt = incident.createdAt
                    )
                )
            } else {
                emptyList()
            }
            flowOf(demoItems)
        } else {
            hazardRepo.observeIncidentEvidence(
                incidentId = incident.id,
                isPublic = incident.visibility == "PUBLIC",
                reporterId = incident.reporterId,
                isAdminEmail = userRepo.isBootstrappedAdminEmail()
            ).catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
        }

    fun updateIncidentDetails(
        incident: IncidentRecord,
        categoryId: String,
        title: String,
        description: String,
        severity: HazardSeverity
    ) {
        viewModelScope.launch {
            val res = hazardRepo.updateIncidentDetails(
                incident = incident,
                categoryId = categoryId,
                title = title,
                description = description,
                severity = severity
            )
            _actionMessage.value = if (res.isSuccess) {
                "Report details updated"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update report"
            }
        }
    }

    fun addIncidentEvidence(
        context: Context,
        incident: IncidentRecord,
        uri: Uri,
        caption: String
    ) {
        viewModelScope.launch {
            val processed = ImageUtils.processSelectedPhoto(context, uri)
            if (processed == null) {
                _actionMessage.value = "Failed to decode selected image."
                return@launch
            }
            val res = hazardRepo.addEvidence(
                incidentId = incident.id,
                fileType = "image/jpeg",
                caption = caption,
                thumbnailBase64 = processed.thumbnailBase64
            )
            _actionMessage.value = if (res.isSuccess) {
                "Evidence attached to incident"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to attach evidence"
            }
        }
    }

    fun updateEvidenceCaption(@Suppress("UNUSED_PARAMETER") incidentId: String, evidenceId: String, caption: String) {
        viewModelScope.launch {
            val res = hazardRepo.updateEvidenceCaption(evidenceId, caption)
            _actionMessage.value = if (res.isSuccess) {
                "Evidence caption updated"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update caption"
            }
        }
    }

    fun deleteIncidentEvidence(@Suppress("UNUSED_PARAMETER") incidentId: String, evidenceId: String) {
        viewModelScope.launch {
            val res = hazardRepo.deleteEvidence(evidenceId)
            _actionMessage.value = if (res.isSuccess) {
                "Evidence removed"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to remove evidence"
            }
        }
    }

    fun deleteIncident(incident: IncidentRecord, onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (incident.isDemoRecord) {
                _actionMessage.value = "Demo records cannot be deleted from Firestore."
                return@launch
            }
            val res = hazardRepo.deleteIncident(
                incidentId = incident.id,
                isAdminEmail = userRepo.isBootstrappedAdminEmail()
            )
            if (res.isSuccess) {
                _actionMessage.value = "Incident deleted"
                onDeleted()
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete incident"
            }
        }
    }

    fun adminUpdateStatus(incident: IncidentRecord, newStatus: IncidentStatus, notes: String) {
        viewModelScope.launch {
            if (!_isAdmin.value) {
                _actionMessage.value = "Unauthorized: Administrator role required."
                return@launch
            }
            val result = hazardRepo.updateIncidentStatus(incident, newStatus, notes)
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Incident updated to ${newStatus.label}"
                    if (incident.reporterId.isNotBlank() && !incident.isDemoRecord) {
                        userRepo.createNotificationForUser(
                            targetUserId = incident.reporterId,
                            incidentId = incident.id,
                            title = "Incident ${newStatus.label}",
                            message = "${incident.title} is now ${newStatus.label}. ${notes.take(120)}",
                            categoryId = incident.categoryId,
                            approximateLocation = incident.locationLabel,
                            incidentStatus = newStatus.name,
                            notificationType = "STATUS_UPDATE"
                        )
                    }
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update incident status"
                }
            )
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { userRepo.markNotificationRead(notificationId) }
    }

    fun updateProfile(name: String, phone: String, regionId: String) {
        viewModelScope.launch {
            val res = userRepo.updateProfile(name, phone, regionId)
            if (res.isSuccess) {
                localPrefs.setSelectedRegionId(regionId)
                _actionMessage.value = "Profile saved"
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun saveNotificationPreferences(
        alertRadiusKm: Double,
        enabledCategories: List<String>,
        frequency: NotificationFrequency,
        watchedRegions: List<String>
    ) {
        viewModelScope.launch {
            val res = userRepo.savePreferences(alertRadiusKm, enabledCategories, frequency, watchedRegions)
            _actionMessage.value = if (res.isSuccess) "Alert preferences updated"
            else res.exceptionOrNull()?.localizedMessage ?: "Failed to save preferences"
        }
    }

    fun buildExportCsv(includeDemo: Boolean): String {
        val real = (adminIncidentsState.value as? UiState.Success)?.data
            ?: (livePublicIncidentsState.value as? UiState.Success)?.data.orEmpty()
        val combined = if (includeDemo) real + DemoDataCatalog.sampleIncidents() else real
        return hazardRepo.buildCsvExport(combined)
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "RoadPulseVM"
    }
}
