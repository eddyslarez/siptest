package com.eddyslarez.siptest.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyslarez.siplibrary.EddysSipLibrary.CallInfo
import com.eddyslarez.siplibrary.EddysSipLibrary.IncomingCallInfo
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siptest.viewmodel.SipViewModel
import com.eddyslarez.siptest.viewmodel.getDisplayText
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    sipViewModel: SipViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by sipViewModel.uiState.collectAsState()
    val callState by sipViewModel.callState.collectAsState()
    val currentCall = uiState.currentCall
    val incomingCall = uiState.incomingCall

    var callDuration by remember { mutableStateOf(0L) }

    // Timer para duración de llamada
    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED) {
            while (callState == CallState.CONNECTED) {
                delay(1000)
                callDuration += 1000
            }
        } else {
            callDuration = 0L
        }
    }

    // Navegar de vuelta cuando la llamada termine
    LaunchedEffect(callState) {
        if (callState == CallState.NONE || callState == CallState.ENDED) {
            delay(2000) // Mostrar mensaje por 2 segundos
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Información de la llamada
        CallInfoSection(
            callState = callState,
            currentCall = currentCall,
            incomingCall = incomingCall,
            callDuration = callDuration,
            message = uiState.callMessage
        )

        // Controles de llamada
        CallControlsSection(
            callState = callState,
            currentCall = currentCall,
            incomingCall = incomingCall,
            onAccept = { sipViewModel.acceptCall() },
            onDecline = { sipViewModel.declineCall() },
            onEnd = { sipViewModel.endCall() },
            onHold = { sipViewModel.holdCall() },
            onResume = { sipViewModel.resumeCall() },
            onMute = { sipViewModel.toggleMute() },
            onDtmf = { digit -> sipViewModel.sendDtmf(digit) }
        )
    }
}

@Composable
fun CallInfoSection(
    callState: CallState,
    currentCall: CallInfo?,
    incomingCall: IncomingCallInfo?,
    callDuration: Long,
    message: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 32.dp)
    ) {
        // Avatar/Icon
        Card(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Caller",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nombre/Número
        val displayName = when {
            incomingCall != null -> incomingCall.callerName ?: incomingCall.callerNumber
            currentCall != null -> currentCall.phoneNumber
            else -> "Unknown"
        }

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Estado de la llamada
        Text(
            text = callState.getDisplayText(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Duración de la llamada
        if (callState == CallState.CONNECTED && callDuration > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(callDuration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Mensaje adicional
        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CallControlsSection(
    callState: CallState,
    currentCall: CallInfo?,
    incomingCall: IncomingCallInfo?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onHold: () -> Unit,
    onResume: () -> Unit,
    onMute: () -> Unit,
    onDtmf: (Char) -> Unit
) {
    when (callState) {
        CallState.INCOMING -> {
            // Botones para llamada entrante
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Declinar
                FloatingActionButton(
                    onClick = onDecline,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Decline",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }

                // Aceptar
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Accept",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        CallState.CONNECTED -> {
            // Controles durante llamada activa
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primera fila de controles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Mute
                    FloatingActionButton(
                        onClick = onMute,
                        containerColor = if (currentCall?.isMuted == true)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (currentCall?.isMuted == true) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (currentCall?.isMuted == true)
                                MaterialTheme.colorScheme.onError
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // Hold/Resume
                    FloatingActionButton(
                        onClick = if (currentCall?.isOnHold == true) onResume else onHold,
                        containerColor = if (currentCall?.isOnHold == true)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (currentCall?.isOnHold == true) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (currentCall?.isOnHold == true) "Resume" else "Hold",
                            tint = if (currentCall?.isOnHold == true)
                                MaterialTheme.colorScheme.onTertiary
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // Speaker
                    FloatingActionButton(
                        onClick = { /* TODO: Implementar cambio de altavoz */ },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Speaker",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón terminar llamada
                FloatingActionButton(
                    onClick = onEnd,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Teclado DTMF
                DtmfKeypad(onDtmf = onDtmf)
            }
        }

        else -> {
            // Estados de transición - solo mostrar botón de terminar
            if (callState in listOf(CallState.CALLING, CallState.RINGING, CallState.OUTGOING)) {
                FloatingActionButton(
                    onClick = onEnd,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Cancel Call",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}

@Composable
fun DtmfKeypad(
    onDtmf: (Char) -> Unit
) {
    val dtmfButtons = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Card(
        modifier = Modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DTMF Keypad",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(4.dp)
            ) {
                dtmfButtons.forEach { row ->
                    row.forEach { digit ->
                        item {
                            Button(
                                onClick = { onDtmf(digit) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .aspectRatio(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}