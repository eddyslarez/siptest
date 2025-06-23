package com.eddyslarez.siptest.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siptest.viewmodel.SipViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    sipViewModel: SipViewModel
) {
    val uiState by sipViewModel.uiState.collectAsState()
    val registrationState by sipViewModel.registrationState.collectAsState()
    val permissionsGranted by sipViewModel.permissionsGranted.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var pushToken by remember { mutableStateOf("test") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SIP Account Registration",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Estado de permisos
                if (!permissionsGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "⚠️ Audio permissions required",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Estado de registro
                RegistrationStatusCard(
                    registrationState = registrationState,
                    message = uiState.registrationMessage
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campo Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    enabled = !uiState.isRegistering && permissionsGranted,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    enabled = !uiState.isRegistering && permissionsGranted,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo Domain
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    enabled = !uiState.isRegistering && permissionsGranted,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo Push Token (opcional)
                OutlinedTextField(
                    value = pushToken,
                    onValueChange = { pushToken = it },
                    label = { Text("Push Token (Optional)") },
                    enabled = !uiState.isRegistering && permissionsGranted,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botón de registro
                Button(
                    onClick = {
                        sipViewModel.registerAccount(username, password, domain, pushToken)
                    },
                    enabled = username.isNotBlank() &&
                            password.isNotBlank() &&
                            !uiState.isRegistering &&
                            permissionsGranted &&
                            registrationState != RegistrationState.OK,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            uiState.isRegistering -> "Registering..."
                            registrationState == RegistrationState.OK -> "Registered ✓"
                            else -> "Register"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RegistrationStatusCard(
    registrationState: RegistrationState,
    message: String
) {
    val (containerColor, contentColor, icon) = when (registrationState) {
        RegistrationState.OK -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "✅"
        )
        RegistrationState.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "❌"
        )
        RegistrationState.PROGRESS, RegistrationState.IN_PROGRESS -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "⏳"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "⚫"
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}