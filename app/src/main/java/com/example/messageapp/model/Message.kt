package com.example.messageapp.model

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val senderId: String = "",
    val type: String = "text",
    val textEnc: String? = null,
    val mediaUrl: String? = null,
    val createdAt: Timestamp? = null,
    val deliveredTo: Map<String, Timestamp> = emptyMap(),
    val readBy: Map<String, Timestamp> = emptyMap()
)