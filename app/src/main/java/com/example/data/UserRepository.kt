package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.HazardCategoryCatalog
import com.example.model.NotificationFrequency
import com.example.model.RegionCatalog
import com.example.model.UserNotification
import com.example.model.UserPreference
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(
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

    fun isBootstrappedAdminEmail(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(ADMIN_EMAIL, ignoreCase = true)
    }

    suspend fun checkIsAdmin(uid: String): Boolean {
        if (isBootstrappedAdminEmail()) return true
        return try {
            val snap = db.collection("admins").document(uid).get().await()
            snap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureUserProfileInitialized(): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = currentUser.uid
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val displayName = currentUser.displayName?.take(80)?.ifBlank { null }
                    ?: currentUser.email?.substringBefore("@")?.take(80)
                    ?: "RoadPulse User"
                val payload = mapOf(
                    "userId" to uid,
                    "name" to displayName,
                    "email" to (currentUser.email?.take(120) ?: ""),
                    "phone" to (currentUser.phoneNumber?.take(30) ?: ""),
                    "role" to "public",
                    "preferredRegionId" to RegionCatalog.defaultRegion.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }

            val prefRef = docRef.collection("preferences").document("settings")
            val prefSnap = prefRef.get().await()
            if (!prefSnap.exists()) {
                val prefPayload = mapOf(
                    "userId" to uid,
                    "alertRadiusKm" to 10.0,
                    "enabledCategories" to HazardCategoryCatalog.categories.map { it.id },
                    "notificationFrequency" to NotificationFrequency.REALTIME.name,
                    "watchedRegions" to listOf(RegionCatalog.defaultRegion.id),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                prefRef.set(prefPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emit(null) // emit initial placeholder until snapshot arrives
        db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun updateProfile(name: String, phone: String, preferredRegionId: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Name cannot be blank." }
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "phone" to phone.trim().take(30),
                    "preferredRegionId" to preferredRegionId.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observePreferences(uid: String): Flow<UserPreference> =
        db.collection("users").document(uid).collection("preferences").document("settings")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(UserPreference::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserPreference(userId = uid)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "users/$uid/preferences/settings")
                }
                throw error
            }

    suspend fun savePreferences(
        alertRadiusKm: Double,
        enabledCategories: List<String>,
        frequency: NotificationFrequency,
        watchedRegions: List<String>
    ): Result<Unit> {
        var path = "users/preferences"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("preferences").document("settings")
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "alertRadiusKm" to alertRadiusKm.coerceIn(1.0, 100.0),
                "enabledCategories" to enabledCategories.take(25),
                "notificationFrequency" to frequency.name,
                "watchedRegions" to watchedRegions.take(10),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeNotifications(uid: String): Flow<List<UserNotification>> =
        db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users/$uid/notifications")
                }
                throw error
            }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        var path = "users/notifications"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notifications").document(notificationId)
            path = docRef.path
            docRef.update(mapOf("read" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createNotificationForUser(
        targetUserId: String,
        incidentId: String?,
        title: String,
        message: String,
        categoryId: String,
        approximateLocation: String,
        incidentStatus: String,
        notificationType: String
    ): Result<Unit> {
        var path = "users/$targetUserId/notifications"
        return try {
            requireUserId()
            val docRef = db.collection("users").document(targetUserId).collection("notifications").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to targetUserId,
                "incidentId" to incidentId?.take(128),
                "title" to title.take(140),
                "message" to message.take(500),
                "categoryId" to categoryId.take(64),
                "approximateLocation" to approximateLocation.take(140),
                "incidentStatus" to incidentStatus,
                "notificationType" to notificationType,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val ADMIN_EMAIL = "joshu1122007@gmail.com"
    }
}
