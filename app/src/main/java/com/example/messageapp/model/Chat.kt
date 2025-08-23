package com.example.messageapp.model

import com.google.firebase.Timestamp

data class Chat(
    val id: String = "",
    val type: String = "direct",
    val members: List<String> = emptyList(),
    val name: String? = null,
    val photoUrl: String? = null,
    val lastMessage: String? = null,
    val updatedAt: Timestamp? = null,
    val pinnedMessageId: String? = null,
    val pinnedSnippet: String? = null
)