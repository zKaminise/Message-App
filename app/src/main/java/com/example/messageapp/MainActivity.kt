package com.example.messageapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.messageapp.ui.auth.AuthScreen
import com.example.messageapp.ui.chat.ChatScreen
import com.example.messageapp.ui.home.HomeScreen
import com.example.messageapp.viewmodel.AuthViewModel
import com.example.messageapp.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val authVm = AuthViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                RequestPostNotificationsOnce()

                val isLogged by authVm.isLogged.collectAsStateWithLifecycle()

                val initialChatId = remember { intent?.getStringExtra("chatId") }
                var consumedChatId by remember { mutableStateOf(false) }

                NavHost(navController = nav, startDestination = "auth") {
                    composable("auth") {
                        AuthScreen(
                            onLoggedIn = {
                                nav.navigate("home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            onOpenChat = { chatId -> nav.navigate("chat/$chatId") },
                            onLoggedOut = {
                                nav.navigate("auth") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )

                        LaunchedEffect(isLogged, initialChatId, consumedChatId) {
                            if (isLogged && initialChatId != null && !consumedChatId) {
                                nav.navigate("chat/$initialChatId")
                                consumedChatId = true
                            } else if (isLogged) {
                                nav.navigate("home") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        }
                    }
                    composable(
                        route = "chat/{chatId}",
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                    ) { backStack ->
                        val chatId = backStack.arguments?.getString("chatId").orEmpty()
                        val chatVm: ChatViewModel = viewModel()
                        ChatScreen(chatId = chatId, vm = chatVm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun RequestPostNotificationsOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    LaunchedEffect(Unit) {
        val perm = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(perm)
    }
}
