package com.example.messageapp.ui.contacts

import android.Manifest
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

data class PhoneContactSimple(val displayName: String, val phone: String)

@Composable
fun rememberDeviceContactsSimple(): Pair<List<PhoneContact>, () -> Unit> {
    val ctx = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<PhoneContact>()) }

    fun read() {
        val cr = ctx.contentResolver
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        val list = mutableListOf<PhoneContact>()
        cursor?.use {
            val iName = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iPhone = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(iName) ?: continue
                val phone = it.getString(iPhone) ?: continue
                list.add(PhoneContact(name, phone))
            }
        }
        contacts = list
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) read() }

    val requestPermission = { launcher.launch(Manifest.permission.READ_CONTACTS) }
    return contacts to requestPermission
}
