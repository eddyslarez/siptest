package com.eddyslarez.siptest.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siptest.viewmodel.SipUiState
import com.eddyslarez.siptest.viewmodel.SipViewModel
import com.eddyslarez.siptest.viewmodel.getDisplayText
import com.eddyslarez.siptest.viewmodel.isCallActive

@Composable
fun SettingsScreen(
    sipViewModel: SipViewModel
) {
    val uiState by sipViewModel.uiState.collectAsState()
    val callState by sipViewModel.callState.collectAsState()
    val registrationState by sipViewModel.registrationState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Estado general del sistema
            SystemStatusCard(
                registrationState = registrationState,
                callState = callState,
                uiState = uiState
            )
        }

        item {
            // Información de la cuenta
            AccountInfoCard(uiState = uiState)
        }

        item {
            // Configuraciones de audio
            AudioSettingsCard()
        }

        item {
            // Información de la biblioteca
            LibraryInfoCard()
        }

        item {
            // Acciones de depuración
            DebugActionsCard(sipViewModel = sipViewModel)
        }
    }
}

@Composable
fun SystemStatusCard(
    registrationState: RegistrationState,
    callState: CallState,
    uiState: SipUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            StatusItem(
                label = "Registration",
                value = registrationState.name,
                icon = Icons.Default.Person,
                isSuccess = registrationState == RegistrationState.OK
            )

            StatusItem(
                label = "Call State",
                value = callState.getDisplayText(),
                icon = Icons.Default.Call,
                isSuccess = callState == CallState.CONNECTED
            )

            StatusItem(
                label = "Active Call",
                value = if (callState.isCallActive()) "Yes" else "No",
                icon = Icons.Default.Phone,
                isSuccess = callState.isCallActive()
            )
        }
    }
}

@Composable
fun AccountInfoCard(
    uiState: com.eddyslarez.siptest.viewmodel.SipUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Account Information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.registeredUsername.isNotEmpty()) {
                InfoItem(
                    label = "Username",
                    value = uiState.registeredUsername,
                    icon = Icons.Default.Person
                )

                InfoItem(
                    label = "Domain",
                    value = uiState.registeredDomain,
                    icon = Icons.Default.Domain
                )

                InfoItem(
                    label = "Status",
                    value = if (uiState.isRegistered) "Registered" else "Not Registered",
                    icon = Icons.Default.CheckCircle
                )
            } else {
                Text(
                    text = "No account registered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AudioSettingsCard() {
    var microphoneEnabled by remember { mutableStateOf(true) }
    var speakerEnabled by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Audio Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingSwitchItem(
                label = "Microphone",
                description = "Enable microphone for calls",
                checked = microphoneEnabled,
                onCheckedChange = { microphoneEnabled = it },
                icon = Icons.Default.Mic
            )

            SettingSwitchItem(
                label = "Speaker",
                description = "Use speaker for audio output",
                checked = speakerEnabled,
                onCheckedChange = { speakerEnabled = it },
                icon = Icons.Default.VolumeUp
            )

            SettingSwitchItem(
                label = "Bluetooth",
                description = "Use Bluetooth audio devices",
                checked = bluetoothEnabled,
                onCheckedChange = { bluetoothEnabled = it },
                icon = Icons.Default.Bluetooth
            )
        }
    }
}

@Composable
fun LibraryInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Library Information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            InfoItem(
                label = "Library",
                value = "EddysSipLibrary",
                icon = Icons.Default.Code
            )

            InfoItem(
                label = "Version",
                value = "1.1.0",
                icon = Icons.Default.Info
            )

            InfoItem(
                label = "Author",
                value = "Eddys Larez",
                icon = Icons.Default.Person
            )

            InfoItem(
                label = "Protocol",
                value = "SIP/WebRTC",
                icon = Icons.Default.Wifi
            )
        }
    }
}

@Composable
fun DebugActionsCard(
    sipViewModel: SipViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Debug Actions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    // Simular llamada de prueba
                    sipViewModel.makeCall("*123")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Call (*123)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // Limpiar estado
                    sipViewModel.clearDialedNumber()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear State")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    // Mostrar información de diagnóstico
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Healing, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Diagnostics")
            }
        }
    }
}

@Composable
fun StatusItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SettingSwitchItem(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}