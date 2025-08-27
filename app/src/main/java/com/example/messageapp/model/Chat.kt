package com.example.messageapp.model

import com.google.firebase.Timestamp

data class Chat(
    val id: String = "",
    val type: String = "direct",
    val name: String? = null,
    val photoUrl: String? = null,
    val ownerId: String? = null,
    val members: List<String> = emptyList(),
    val visibleFor: List<String>? = null,
    val lastMessage: String? = null,
    val lastMessageEnc: String? = null,
    val pinnedSnippet: String? = null,
    val updatedAt: Timestamp? = null
)
