package com.example.messageapp.model

data class User(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val bio: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val fcmTokens: List<String> = emptyList()
)