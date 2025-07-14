package com.eddyslarez.siptest.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.EddysSipLibrary.CallInfo
import com.eddyslarez.siplibrary.EddysSipLibrary.IncomingCallInfo
import com.eddyslarez.siplibrary.data.models.CallErrorReason
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.CallStateInfo
import com.eddyslarez.siplibrary.data.models.SipErrorMapper
import com.eddyslarez.siptest.viewmodel.SipViewModel
import com.eddyslarez.siptest.viewmodel.getDisplayText
import com.eddyslarez.siptest.viewmodel.isCallActive
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    sipViewModel: SipViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by sipViewModel.uiState.collectAsState()

    // OPTIMIZADO: Estados unificados
    val callState by sipViewModel.callState.collectAsState()
    val callDuration by sipViewModel.callDuration.collectAsState()

    val currentCall = uiState.currentCall
    val incomingCall = uiState.incomingCall

    // OPTIMIZADO: Navegar de vuelta cuando la llamada termine usando estados unificados
    LaunchedEffect(callState.state) {
        if (callState.state == CallState.ENDED ||
            callState.state == CallState.IDLE) {
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
        // OPTIMIZADO: Información de la llamada con estados unificados
        CallInfoSection(
            callState = callState,
            currentCall = currentCall,
            incomingCall = incomingCall,
            callDuration = callDuration,
            message = uiState.callMessage,
            detailedMessage = uiState.detailedCallMessage,
            hasError = uiState.hasCallError,
            errorReason = uiState.errorReason
        )

        // OPTIMIZADO: Controles de llamada con estados unificados
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

// OPTIMIZADO: Sección de información con estados unificados
@Composable
fun CallInfoSection(
    callState: CallStateInfo,
    currentCall: EddysSipLibrary.CallInfo?,
    incomingCall: EddysSipLibrary.IncomingCallInfo?,
    callDuration: Long,
    message: String,
    detailedMessage: String,
    hasError: Boolean,
    errorReason: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 32.dp)
    ) {
        // Avatar/Icon con estado visual
        val avatarColor = when {
            hasError -> MaterialTheme.colorScheme.errorContainer
            callState.state == CallState.STREAMS_RUNNING -> MaterialTheme.colorScheme.primaryContainer
            callState.state.isCallActive() -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Card(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = avatarColor)
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

                // Indicador de estado en la esquina
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .background(
                            color = when (callState.state) {
                                CallState.STREAMS_RUNNING -> Color.Green
                                CallState.PAUSED -> Color.Yellow
                                CallState.ERROR -> Color.Red
                                else -> Color.Gray
                            },
                            shape = CircleShape
                        )
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
            text = callState.state.getDisplayText(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (hasError) FontWeight.Bold else FontWeight.Normal
        )

        // Duración de la llamada
        if (callState.state == CallState.STREAMS_RUNNING && callDuration > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(callDuration),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Mensaje detallado
        if (detailedMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = detailedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Información de error
        if (hasError && errorReason != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "❌ ${SipErrorMapper.getErrorDescription(CallErrorReason.valueOf(errorReason))}",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Información SIP (solo en debug)

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SIP: ${callState.sipReason ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

}

// OPTIMIZADO: Controles con estados unificados
@Composable
fun CallControlsSection(
    callState: CallStateInfo,
    currentCall: EddysSipLibrary.CallInfo?,
    incomingCall: EddysSipLibrary.IncomingCallInfo?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onHold: () -> Unit,
    onResume: () -> Unit,
    onMute: () -> Unit,
    onDtmf: (Char) -> Unit
) {
    when (callState.state) {
        CallState.INCOMING_RECEIVED -> {
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

        CallState.STREAMS_RUNNING, CallState.CONNECTED -> {
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
                        onClick = if (callState.state == CallState.PAUSED) onResume else onHold,
                        containerColor = if (callState.state == CallState.PAUSED)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (callState.state == CallState.PAUSED)
                                Icons.Default.PlayArrow
                            else
                                Icons.Default.Pause,
                            contentDescription = if (callState.state == CallState.PAUSED)
                                "Resume"
                            else
                                "Hold",
                            tint = if (callState.state == CallState.PAUSED)
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

                // Teclado DTMF solo si está en streams running
                if (callState.state == CallState.STREAMS_RUNNING) {
                    DtmfKeypad(onDtmf = onDtmf)
                }
            }
        }

        CallState.PAUSED -> {
            // Controles para llamada en hold
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Botón resume prominente
                FloatingActionButton(
                    onClick = onResume,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Resume Call",
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón terminar
                FloatingActionButton(
                    onClick = onEnd,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }

        CallState.OUTGOING_INIT,
        CallState.OUTGOING_PROGRESS,
        CallState.OUTGOING_RINGING -> {
            // Estados de llamada saliente - solo mostrar botón de cancelar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                Spacer(modifier = Modifier.height(8.dp))

                // Mostrar estado específico
                Text(
                    text = when (callState.state) {
                        CallState.OUTGOING_INIT -> "Iniciando llamada..."
                        CallState.OUTGOING_PROGRESS -> "Conectando..."
                        CallState.OUTGOING_RINGING -> "Sonando..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        CallState.ERROR -> {
            // Estado de error - mostrar información y botón de cerrar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingActionButton(
                    onClick = onEnd,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Toca para cerrar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            // Estados de transición - mostrar indicador de carga
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = callState.state.getDisplayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


//@Composable
//fun CallScreen(
//    sipViewModel: SipViewModel,
//    onNavigateBack: () -> Unit
//) {
//    val uiState by sipViewModel.uiState.collectAsState()
//    val callState by sipViewModel.callState.collectAsState()
//    val currentCall = uiState.currentCall
//    val incomingCall = uiState.incomingCall
//
//    var callDuration by remember { mutableStateOf(0L) }
//
//    // Timer para duración de llamada
//    LaunchedEffect(callState) {
//        if (callState == CallState.CONNECTED) {
//            while (callState == CallState.CONNECTED) {
//                delay(1000)
//                callDuration += 1000
//            }
//        } else {
//            callDuration = 0L
//        }
//    }
//
//    // Navegar de vuelta cuando la llamada termine
//    LaunchedEffect(callState) {
//        if (callState == CallState.NONE || callState == CallState.ENDED) {
//            delay(2000) // Mostrar mensaje por 2 segundos
//            onNavigateBack()
//        }
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.SpaceBetween
//    ) {
//        // Información de la llamada
//        CallInfoSection(
//            callState = callState,
//            currentCall = currentCall,
//            incomingCall = incomingCall,
//            callDuration = callDuration,
//            message = uiState.callMessage
//        )
//
//        // Controles de llamada
//        CallControlsSection(
//            callState = callState,
//            currentCall = currentCall,
//            incomingCall = incomingCall,
//            onAccept = { sipViewModel.acceptCall() },
//            onDecline = { sipViewModel.declineCall() },
//            onEnd = { sipViewModel.endCall() },
//            onHold = { sipViewModel.holdCall() },
//            onResume = { sipViewModel.resumeCall() },
//            onMute = { sipViewModel.toggleMute() },
//            onDtmf = { digit -> sipViewModel.sendDtmf(digit) }
//        )
//    }
//}

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
        CallState.INCOMING_RECEIVED -> {
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
            if (callState in listOf(CallState.OUTGOING_PROGRESS, CallState.OUTGOING_RINGING, CallState.OUTGOING_INIT)) {
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