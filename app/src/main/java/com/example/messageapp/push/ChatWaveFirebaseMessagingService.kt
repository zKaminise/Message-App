package com.example.messageapp.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.messageapp.MainActivity
import com.example.messageapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ChatWaveFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val chatId = message.data["chatId"] // pode ser null se a função não mandar
        val title  = message.notification?.title ?: "Nova mensagem"
        val body   = message.notification?.body  ?: "Você recebeu uma mensagem."

        // Canal único p/ todas as mensagens
        ensureChannel()

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (chatId != null) intent.putExtra("chatId", chatId)

        val pending = PendingIntent.getActivity(
            this,
            (chatId?.hashCode() ?: 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "messages")
            .setSmallIcon(R.mipmap.ic_launcher) // troque por um ícone seu se quiser
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Android 13+: precisa da permissão em runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        // Usa um ID estável por chat (agrupa notificações do mesmo chat)
        val notifyId = chatId?.hashCode() ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        NotificationManagerCompat.from(this).notify(notifyId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                "messages",
                "Mensagens",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
    }
}
