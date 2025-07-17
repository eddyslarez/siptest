package com.eddyslarez.siptest.screens

import android.annotation.SuppressLint
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
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
import java.io.File

@Composable
fun CallScreen(
    sipViewModel: SipViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by sipViewModel.uiState.collectAsState()
    val callState by sipViewModel.callState.collectAsState()
    val callDuration by sipViewModel.callDuration.collectAsState()

    // NUEVO: Estados de audio
    val isRecordingSent by sipViewModel.isRecordingSentAudio.collectAsState()
    val isRecordingReceived by sipViewModel.isRecordingReceivedAudio.collectAsState()
    val isPlayingInputFile by sipViewModel.isPlayingInputFile.collectAsState()
    val isPlayingOutputFile by sipViewModel.isPlayingOutputFile.collectAsState()

    val currentCall = uiState.currentCall
    val incomingCall = uiState.incomingCall

    // Navegar de vuelta cuando la llamada termine
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
        // Información de la llamada
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

        // NUEVO: Indicadores de estado de audio
        AudioStatusSection(
            isRecordingSent = isRecordingSent,
            isRecordingReceived = isRecordingReceived,
            isPlayingInputFile = isPlayingInputFile,
            isPlayingOutputFile = isPlayingOutputFile,
            currentInputFile = sipViewModel.getCurrentInputAudioFile(),
            currentOutputFile = sipViewModel.getCurrentOutputAudioFile()
        )

        // Controles de llamada con funciones de audio
        CallControlsSection(
            callState = callState,
            currentCall = currentCall,
            incomingCall = incomingCall,
            sipViewModel = sipViewModel,
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

// NUEVO: Sección de estado de audio
@Composable
fun AudioStatusSection(
    isRecordingSent: Boolean,
    isRecordingReceived: Boolean,
    isPlayingInputFile: Boolean,
    isPlayingOutputFile: Boolean,
    currentInputFile: String?,
    currentOutputFile: String?
) {
    if (isRecordingSent || isRecordingReceived || isPlayingInputFile || isPlayingOutputFile) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "🎵 Estado del Audio",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isRecordingSent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grabando audio enviado",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isRecordingReceived) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grabando audio recibido",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isPlayingInputFile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reproduciendo: ${File(currentInputFile ?: "").name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isPlayingOutputFile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reproduciendo salida: ${File(currentOutputFile ?: "").name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// OPTIMIZADO: Controles con estados unificados
@Composable
fun CallControlsSection(
    callState: CallStateInfo,
    currentCall: EddysSipLibrary.CallInfo?,
    incomingCall: EddysSipLibrary.IncomingCallInfo?,
    sipViewModel: SipViewModel,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onHold: () -> Unit,
    onResume: () -> Unit,
    onMute: () -> Unit,
    onDtmf: (Char) -> Unit
) {
    when (callState.state) {
        CallState.STREAMS_RUNNING, CallState.CONNECTED -> {
            // Controles durante llamada activa
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // NUEVO: Controles de audio expandidos
                AudioControlsExpandedSection(sipViewModel = sipViewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // Controles básicos de llamada
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

                // Teclado DTMF
                DtmfKeypad(onDtmf = onDtmf)
            }
        }

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
        else -> {
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

// NUEVO: Sección expandida de controles de audio
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun AudioControlsExpandedSection(
    sipViewModel: SipViewModel
) {
    var showAudioControls by remember { mutableStateOf(false) }

    Column {
        // Botón para mostrar/ocultar controles de audio
        TextButton(
            onClick = { showAudioControls = !showAudioControls }
        ) {
            Icon(
                if (showAudioControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Controles de Audio")
        }

        // Controles de audio expandibles
        AnimatedVisibility(visible = showAudioControls) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Controles de grabación
                    Text(
                        text = "📹 Grabación",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Grabar audio enviado
                        Button(
                            onClick = {
                                if (sipViewModel.isRecordingSentAudio.value) {
                                    sipViewModel.stopRecordingSentAudio()
                                } else {
                                    sipViewModel.startRecordingSentAudio()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sipViewModel.isRecordingSentAudio.value)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (sipViewModel.isRecordingSentAudio.value)
                                    Icons.Default.Stop
                                else
                                    Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (sipViewModel.isRecordingSentAudio.value) "Detener" else "Grabar",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Grabar audio recibido
                        Button(
                            onClick = {
                                if (sipViewModel.isRecordingReceivedAudio.value) {
                                    sipViewModel.stopRecordingReceivedAudio()
                                } else {
                                    sipViewModel.startRecordingReceivedAudio()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sipViewModel.isRecordingReceivedAudio.value)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                if (sipViewModel.isRecordingReceivedAudio.value)
                                    Icons.Default.Stop
                                else
                                    Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (sipViewModel.isRecordingReceivedAudio.value) "Detener" else "Recibido",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Controles de reproducción
                    Text(
                        text = "🎵 Reproducción",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath +
                            "/smb_gameover.wav"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Reproducir archivo de entrada
                        Button(
                            onClick = {
                                if (sipViewModel.isPlayingInputFile.value) {
                                    sipViewModel.stopPlayingInputAudioFile()
                                } else {
                                    // Aquí deberías mostrar un selector de archivos
                                    // Por ahora usamos un archivo de ejemplo
                                    sipViewModel.startPlayingInputAudioFile(path)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sipViewModel.isPlayingInputFile.value)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(
                                if (sipViewModel.isPlayingInputFile.value)
                                    Icons.Default.Stop
                                else
                                    Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (sipViewModel.isPlayingInputFile.value) "Detener" else "Entrada",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }


                        // Reproducir archivo de salida
                        Button(
                            onClick = {
                                if (sipViewModel.isPlayingOutputFile.value) {
                                    sipViewModel.stopPlayingOutputAudioFile()
                                } else {
                                    // Por ahora usamos un archivo de ejemplo
                                    sipViewModel.startPlayingOutputAudioFile(path)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sipViewModel.isPlayingOutputFile.value)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(
                                if (sipViewModel.isPlayingOutputFile.value)
                                    Icons.Default.Stop
                                else
                                    Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (sipViewModel.isPlayingOutputFile.value) "Detener" else "Salida",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón para ver grabaciones
                    Button(
                        onClick = {
                            // Navegar a pantalla de grabaciones
                            sipViewModel.showRecordedFiles()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Grabaciones")
                    }
                }
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
    callState: CallStateInfo,
    currentCall: CallInfo?,
    incomingCall: IncomingCallInfo?,
    callDuration: Long,
    message: String,
    detailedMessage: String,
    hasError: Boolean,
    errorReason: String?,
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
            text = callState.state.getDisplayText(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Duración de la llamada
        if (callState.state == CallState.CONNECTED && callDuration > 0) {
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