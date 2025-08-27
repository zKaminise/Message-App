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
import com.example.messageapp.data.AuthRepository
import com.example.messageapp.ui.auth.AuthScreen
import com.example.messageapp.ui.chat.ChatScreen
import com.example.messageapp.ui.chatlist.ChatListScreen
import com.example.messageapp.ui.contacts.ContactsScreen
import com.example.messageapp.ui.groups.GroupCreateScreen
import com.example.messageapp.ui.profile.ProfileScreen
import com.example.messageapp.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.messageapp.utils.SignatureLogger

class MainActivity : ComponentActivity() {

    private val authVm = AuthViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                SignatureLogger.log(this@MainActivity)
            }
            MaterialTheme {
                val nav = rememberNavController()
                RequestPostNotificationsOnce()

                val isLogged by authVm.isLogged.collectAsStateWithLifecycle()
                val initialChatId = remember { intent?.getStringExtra("chatId") }
                var consumedChatId by remember { mutableStateOf(false) }

                NavHost(
                    navController = nav,
                    startDestination = if (isLogged) "home" else "auth"
                ) {
                    composable("auth") {
                        val repo = remember { AuthRepository() }
                        AuthScreen(repo = repo) {
                            nav.navigate("home") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    }
                    composable("home") {
                        HomeWrapper(
                            openChat = { id -> nav.navigate("chat/$id") },
                            openContacts = { nav.navigate("contacts") },
                            openNewGroup = { nav.navigate("groupNew") },
                            openProfile = { nav.navigate("profile") },
                            logout = { nav.navigate("auth") { popUpTo(0) } }
                        )
                        LaunchedEffect(isLogged, initialChatId, consumedChatId) {
                            if (isLogged && initialChatId != null && !consumedChatId) {
                                nav.navigate("chat/$initialChatId")
                                consumedChatId = true
                            }
                        }
                    }
                    composable("contacts") {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        ContactsScreen(
                            myUid = uid,
                            onOpenChat = { id ->
                                nav.navigate("chat/$id") { popUpTo("contacts") { inclusive = true } }
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("groupNew") {
                        GroupCreateScreen(
                            onCreated = { id -> nav.navigate("chat/$id") { popUpTo("home") { inclusive = true } } },
                            onCancel = { nav.popBackStack() },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("profile") {
                        ProfileScreen(
                            onLoggedOut = {
                                nav.navigate("auth") { popUpTo(0) }
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }

                    composable(
                        "chat/{chatId}",
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                    ) { backStack ->
                        val chatId = backStack.arguments?.getString("chatId").orEmpty()
                        val vm: com.example.messageapp.viewmodel.ChatViewModel = viewModel()
                        ChatScreen(
                            chatId = chatId,
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onOpenInfo = { id -> nav.navigate("chatInfo/$id") } // ⬅️ NOVO
                        )
                    }

                    composable(
                        "chatInfo/{chatId}",
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                    ) { backStack ->
                        val chatId = backStack.arguments?.getString("chatId").orEmpty()
                        com.example.messageapp.ui.chat.ChatInfoScreen(
                            chatId = chatId,
                            onBack = { nav.popBackStack() }
                        )
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
private fun HomeWrapper(
    openChat: (String) -> Unit,
    openContacts: () -> Unit,
    openNewGroup: () -> Unit,
    openProfile: () -> Unit,
    logout: () -> Unit
) {
    val vm: com.example.messageapp.viewmodel.ChatListViewModel = viewModel()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    ChatListScreen(
        myUid = myUid,
        vm = vm,
        onOpenChat = openChat,
        onOpenContacts = openContacts,
        onOpenNewGroup = openNewGroup,
        onOpenProfile = openProfile,
        onLogout = logout
    )
}

@Composable
private fun RequestPostNotificationsOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val perm = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(perm)
    }
}
