package com.komet.smartreminder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.komet.smartreminder.data.model.Appointment
import com.komet.smartreminder.email.GmailScanner
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.onPermissionsGranted() }
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
        viewModel.scanEmails()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authenticate()
    }

    private fun authenticate() {
        val bm = BiometricManager.from(this)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { showApp() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) showApp() else finish()
                }
            }).authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Smart Reminder").setSubtitle("Unlock to view appointments")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build())
        } else showApp()
    }

    private fun showApp() {
        requestPermissions()
        setContent { MaterialTheme { SmartReminderApp(viewModel, onSignIn = {
            signInLauncher.launch(GoogleSignIn.getClient(this, GmailScanner.getSignInOptions()).signInIntent)
        }) } }
    }

    private fun requestPermissions() {
        val perms = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)
        if (!perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            permLauncher.launch(perms)
        } else viewModel.onPermissionsGranted()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartReminderApp(viewModel: MainViewModel, onSignIn: () -> Unit) {
    val appointments by viewModel.appointments.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📅 Smart Reminder") },
                actions = {
                    IconButton(onClick = { viewModel.scanAll() }) { Icon(Icons.Default.Refresh, "Scan") }
                    IconButton(onClick = { onSignIn() }) { Icon(Icons.Default.Email, "Gmail") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Calendar strip
            CalendarStrip(selectedDate, onDateSelected = { viewModel.selectDate(it) })

            // Appointments for selected date
            val dayAppointments = appointments.filter { it.date == selectedDate }
            if (dayAppointments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No appointments on this day", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(dayAppointments) { apt -> AppointmentCard(apt, onDismiss = { viewModel.dismiss(it) }) }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(viewModel, onDismiss = { showSettings = false })
    }
}

@Composable
fun CalendarStrip(selectedDate: String, onDateSelected: (String) -> Unit) {
    val today = LocalDate.now()
    val days = (-3..14).map { today.plusDays(it.toLong()) }

    LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(days) { day ->
            val dateStr = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val isSelected = dateStr == selectedDate
            val isToday = day == today
            Column(
                modifier = Modifier
                    .clickable { onDateSelected(dateStr) }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else Color.Gray)
                Text("${day.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Color.White else Color.Unspecified)
            }
        }
    }
}

@Composable
fun AppointmentCard(apt: Appointment, onDismiss: (Appointment) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(apt.title, style = MaterialTheme.typography.titleSmall)
                if (apt.time.isNotEmpty()) Text("🕐 ${apt.time}", style = MaterialTheme.typography.bodySmall)
                if (apt.location.isNotEmpty()) Text("📍 ${apt.location}", style = MaterialTheme.typography.bodySmall)
                Text("via ${apt.source}: ${apt.sourceDetail.take(30)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            IconButton(onClick = { onDismiss(apt) }) { Icon(Icons.Default.Close, "Remove", tint = Color.Gray) }
        }
    }
}

@Composable
fun SettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var hour by remember { mutableStateOf("20") }
    var minute by remember { mutableStateOf("00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Daily reminder time (for next day's appointments):")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text("Hour") }, modifier = Modifier.width(80.dp))
                    Text(":")
                    OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text("Min") }, modifier = Modifier.width(80.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateReminderTime(hour.toIntOrNull() ?: 20, minute.toIntOrNull() ?: 0)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
