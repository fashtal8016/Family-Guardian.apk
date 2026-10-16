package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.FamilyLink
import com.example.data.model.LocationRecord
import com.example.data.model.LocationRequest
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FamilyRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---

    suspend fun getOrCreateUserProfile(displayName: String, email: String, role: String): Result<UserProfile> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile(userId = uid, displayName = displayName, email = email, role = role)
                Result.success(profile)
            } else {
                val newProfile = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.ifBlank { "User" },
                    "email" to email,
                    "role" to role,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(newProfile).await()
                Result.success(UserProfile(userId = uid, displayName = displayName, email = email, role = role))
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Family Links ---

    fun observeChildrenLinks(parentId: String): Flow<List<FamilyLink>> {
        val colRef = db.collection("family_links")
        return colRef.whereEqualTo("parentId", parentId)
            .snapshots()
            .map { snap -> snap.toObjects(FamilyLink::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    fun observeChildLink(linkId: String): Flow<FamilyLink?> {
        val docRef = db.collection("family_links").document(linkId)
        return docRef.snapshots()
            .map { snap -> snap.toObject(FamilyLink::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
    }

    suspend fun createChildLink(childName: String, pairingCode: String): Result<String> {
        val parentUid = requireUserId()
        val colRef = db.collection("family_links")
        return try {
            val docRef = colRef.document()
            val payload = mapOf(
                "parentId" to parentUid,
                "childId" to "pending_${docRef.id}",
                "childName" to childName,
                "pairingCode" to pairingCode,
                "status" to "pending",
                "batteryLevel" to 100,
                "isOnline" to false,
                "isSharingLocation" to true,
                "locationPermissionStatus" to "pending",
                "gpsEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun connectChildWithPairingCode(pairingCode: String, childDeviceId: String, deviceModel: String): Result<FamilyLink> {
        val colRef = db.collection("family_links")
        return try {
            val query = colRef.whereEqualTo("pairingCode", pairingCode).limit(1).get().await()
            if (query.isEmpty) {
                return Result.failure(IllegalArgumentException("کد اتصال نامعتبر است یا یافت نشد."))
            }
            val doc = query.documents.first()
            val linkId = doc.id
            val updatePayload = mapOf(
                "childId" to childDeviceId,
                "deviceModel" to deviceModel,
                "status" to "active",
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            colRef.document(linkId).update(updatePayload).await()
            val updated = colRef.document(linkId).get().await().toObject(FamilyLink::class.java)
                ?: throw IllegalStateException("خطا در بارگذاری اطلاعات دستگاه")
            Result.success(updated)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChildTelemetry(
        linkId: String,
        batteryLevel: Int,
        isOnline: Boolean,
        isSharing: Boolean,
        permStatus: String,
        gpsEnabled: Boolean
    ): Result<Unit> {
        val docRef = db.collection("family_links").document(linkId)
        return try {
            val payload = mapOf(
                "batteryLevel" to batteryLevel.coerceIn(0, 100),
                "isOnline" to isOnline,
                "isSharingLocation" to isSharing,
                "locationPermissionStatus" to permStatus,
                "gpsEnabled" to gpsEnabled,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun unlinkChild(linkId: String): Result<Unit> {
        val docRef = db.collection("family_links").document(linkId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Locations ---

    suspend fun reportLocation(
        parentId: String,
        childId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        provider: String = "fused",
        speed: Double = 0.0
    ): Result<String> {
        val colRef = db.collection("locations")
        return try {
            val docRef = colRef.document()
            val payload = mapOf(
                "parentId" to parentId,
                "childId" to childId,
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "provider" to provider,
                "speed" to speed,
                "recordedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    fun observeLocations(parentId: String, childId: String): Flow<List<LocationRecord>> {
        val colRef = db.collection("locations")
        return colRef.whereEqualTo("parentId", parentId)
            .whereEqualTo("childId", childId)
            .snapshots()
            .map { snap ->
                snap.toObjects(LocationRecord::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    // --- Messages ---

    suspend fun sendMessage(
        parentId: String,
        childId: String,
        senderType: String,
        text: String
    ): Result<String> {
        val colRef = db.collection("messages")
        return try {
            val docRef = colRef.document()
            val payload = mapOf(
                "parentId" to parentId,
                "childId" to childId,
                "senderType" to senderType,
                "text" to text.trim(),
                "isRead" to false,
                "timestamp" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    fun observeMessages(parentId: String, childId: String): Flow<List<ChatMessage>> {
        val colRef = db.collection("messages")
        return colRef.whereEqualTo("parentId", parentId)
            .whereEqualTo("childId", childId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java)
                    .sortedBy { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    suspend fun markMessageAsRead(messageId: String): Result<Unit> {
        val docRef = db.collection("messages").document(messageId)
        return try {
            docRef.update("isRead", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Location Requests ---

    suspend fun requestLocation(parentId: String, childId: String, note: String = ""): Result<String> {
        val colRef = db.collection("location_requests")
        return try {
            val docRef = colRef.document()
            val payload = mapOf(
                "parentId" to parentId,
                "childId" to childId,
                "status" to "pending",
                "message" to note,
                "requestedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    fun observePendingRequestsForChild(childId: String): Flow<List<LocationRequest>> {
        val colRef = db.collection("location_requests")
        return colRef.whereEqualTo("childId", childId)
            .whereEqualTo("status", "pending")
            .snapshots()
            .map { snap -> snap.toObjects(LocationRequest::class.java) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    suspend fun updateLocationRequestStatus(requestId: String, status: String): Result<Unit> {
        val docRef = db.collection("location_requests").document(requestId)
        return try {
            val payload = mapOf(
                "status" to status,
                "fulfilledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
