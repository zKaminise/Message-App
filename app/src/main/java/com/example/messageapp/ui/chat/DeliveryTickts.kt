package com.example.messageapp.ui.chat

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll

@Composable
fun DeliveryTicks(sent: Boolean, delivered: Boolean, read: Boolean) {
    val tint: Color = when {
        read -> MaterialTheme.colorScheme.primary
        delivered -> MaterialTheme.colorScheme.onSurface
        sent -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    when {
        read || delivered -> Icon(Icons.Default.DoneAll, contentDescription = null, tint = tint)
        sent -> Icon(Icons.Default.Done, contentDescription = null, tint = tint)
    }
}
