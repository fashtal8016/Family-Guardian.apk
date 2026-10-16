package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "parent", // "parent" or "child"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyLink(
    @DocumentId val id: String = "",
    val parentId: String = "",
    val childId: String = "",
    val childName: String = "",
    val deviceModel: String = "",
    val batteryLevel: Int = 100,
    val isOnline: Boolean = true,
    val isSharingLocation: Boolean = true,
    val locationPermissionStatus: String = "granted", // "granted", "denied", "foreground_only"
    val gpsEnabled: Boolean = true,
    val pairingCode: String = "",
    val status: String = "active", // "pending", "active", "unlinked"
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LocationRecord(
    @DocumentId val id: String = "",
    val parentId: String = "",
    val childId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Double = 0.0,
    val provider: String = "fused",
    val speed: Double = 0.0,
    val recordedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val parentId: String = "",
    val childId: String = "",
    val senderType: String = "parent", // "parent" or "child"
    val text: String = "",
    val isRead: Boolean = false,
    val timestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LocationRequest(
    @DocumentId val id: String = "",
    val parentId: String = "",
    val childId: String = "",
    val status: String = "pending", // "pending", "fulfilled", "rejected", "gps_disabled", "permission_denied"
    val message: String = "",
    val requestedAt: Timestamp? = null,
    val fulfilledAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
