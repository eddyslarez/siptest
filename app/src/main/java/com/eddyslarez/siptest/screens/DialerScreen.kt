package com.eddyslarez.siptest.screens
import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyslarez.siplibrary.data.models.CallErrorReason
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.CallStateInfo
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.models.SipErrorMapper
import com.eddyslarez.siptest.viewmodel.SipViewModel
import com.eddyslarez.siptest.viewmodel.getDisplayText
import com.eddyslarez.siptest.viewmodel.isCallActive
import kotlinx.coroutines.delay

@Composable
fun DialerScreen(
    sipViewModel: SipViewModel
) {
    val uiState by sipViewModel.uiState.collectAsState()

    // CORREGIDO: Estados unificados con manejo correcto
    val callState by sipViewModel.callState.collectAsState()
    val registrationStates by sipViewModel.registrationStates.collectAsState()
    val callDuration by sipViewModel.callDuration.collectAsState()

    // AGREGADO: Calcular si hay al menos una cuenta registrada
    val hasRegisteredAccount = registrationStates.values.any { it == RegistrationState.OK }
    val isRegistrationInProgress = registrationStates.values.any { it == RegistrationState.IN_PROGRESS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // CORREGIDO: Estado de la llamada con información unificada
        CallStatusCard(
            callState = callState,
            message = uiState.callMessage,
            detailedMessage = uiState.detailedCallMessage,
            hasError = uiState.hasCallError,
            errorReason = uiState.errorReason,
            duration = callDuration
        )

        Spacer(modifier = Modifier.height(16.dp))

        // CORREGIDO: Estado de múltiples cuentas
        if (registrationStates.isNotEmpty()) {
            MultiAccountStatusCard(
                registrationStates = registrationStates,
                multiAccountStatus = uiState.multiAccountStatus
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Número marcado
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = if (uiState.dialedNumber.isEmpty()) "Enter number" else uiState.dialedNumber,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = if (uiState.dialedNumber.isEmpty())
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CORREGIDO: Teclado numérico con lógica de habilitación mejorada
        ModernDialPad(
            onDigitClick = { digit ->
                sipViewModel.updateDialedNumber(uiState.dialedNumber + digit)
            },
            onBackspaceClick = {
                val currentNumber = uiState.dialedNumber
                if (currentNumber.isNotEmpty()) {
                    sipViewModel.updateDialedNumber(currentNumber.dropLast(1))
                }
            },
            onCallClick = {
                if (uiState.dialedNumber.isNotEmpty()) {
                    sipViewModel.makeCall(uiState.dialedNumber)
                }
            },
            enabled = hasRegisteredAccount && !callState.state.isCallActive(),
            hasNumber = uiState.dialedNumber.isNotEmpty(),
            isRegistrationInProgress = isRegistrationInProgress
        )

        Spacer(modifier = Modifier.height(16.dp))

        // CORREGIDO: Estado de registro con información más detallada
        if (!hasRegisteredAccount && !isRegistrationInProgress) {
            RegistrationStatusCard(
                registrationStates = registrationStates,
                isEmpty = registrationStates.isEmpty()
            )
        }

        // CORREGIDO: Información de debugging mejorada

            Spacer(modifier = Modifier.height(16.dp))
            DebugInfoCard(
                callState = callState,
                lastTransition = uiState.lastStateTransition,
                registrationStates = registrationStates,
                onShowDiagnostic = {
                    Log.d("SipDebug", sipViewModel.getSystemDiagnostic())
                }
            )

    }
}

@Composable
fun CallStatusCard(
    callState: CallStateInfo,
    message: String,
    detailedMessage: String,
    hasError: Boolean,
    errorReason: String?,
    duration: Long
) {
    val (containerColor, contentColor, icon) = when {
        hasError -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "❌"
        )
        callState.state == CallState.STREAMS_RUNNING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "📞"
        )
        callState.state.isCallActive() -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "📱"
        )
        callState.state == CallState.INCOMING_RECEIVED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "📲"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "📴"
        )
    }

    // --- 1. Calcula la descripción de error fuera del UI ---
    val errorText: String? = if (hasError && errorReason != null) {
        runCatching {
            "Error: " + SipErrorMapper.getErrorDescription(
                CallErrorReason.valueOf(errorReason)
            )
        }.getOrElse { "Error: $errorReason" }
    } else null
    // -------------------------------------------------------

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callState.state.getDisplayText(),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (detailedMessage.isNotEmpty()) {
                        Text(
                            text = detailedMessage,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (duration > 0 && callState.state == CallState.STREAMS_RUNNING) {
                    Text(
                        text = formatDuration(duration),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- 2. Muestra el texto de error ya calculado ---
            if (errorText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorText,
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
            }
            // --------------------------------------------------

            if (callState.sipCode != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SIP: ${callState.sipCode} ${callState.sipReason.orEmpty()}",
                    color = contentColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}


// CORREGIDO: Card para estado de múltiples cuentas
@Composable
fun MultiAccountStatusCard(
    registrationStates: Map<String, RegistrationState>,
    multiAccountStatus: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "📋 $multiAccountStatus",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            registrationStates.forEach { (account, state) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = account,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )

                    val (stateIcon, stateColor) = when (state) {
                        RegistrationState.OK -> "✅" to MaterialTheme.colorScheme.primary
                        RegistrationState.FAILED -> "❌" to MaterialTheme.colorScheme.error
                        RegistrationState.IN_PROGRESS -> "🔄" to MaterialTheme.colorScheme.secondary
                        RegistrationState.NONE -> "⭕" to MaterialTheme.colorScheme.onSurfaceVariant
                        RegistrationState.CLEARED -> "🔲" to MaterialTheme.colorScheme.onSurfaceVariant
                        else -> "⚪" to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stateIcon,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = state.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor
                        )
                    }
                }

                if (registrationStates.entries.last().key != account) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
data class StatusInfo(
    val containerColor: Color,
    val contentColor: Color,
    val icon: String,
    val message: String
)

@Composable
fun RegistrationStatusCard(
    registrationStates: Map<String, RegistrationState>,
    isEmpty: Boolean
) {
    // Devuelve StatusInfo en vez de Triple
    val status = when {
        isEmpty -> StatusInfo(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⚠️",
            "No SIP accounts configured"
        )
        registrationStates.values.all { it == RegistrationState.FAILED } -> StatusInfo(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "❌",
            "All accounts failed to register"
        )
        else -> StatusInfo(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⚠️",
            "Please register a SIP account first"
        )
    }

    // Desestructuración totalmente válida
    val (containerColor, contentColor, icon, message) = status

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    message,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (registrationStates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Accounts: ${registrationStates.keys.joinToString(", ")}",
                    color = contentColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


// CORREGIDO: Card de información de debugging mejorada
@Composable
fun DebugInfoCard(
    callState: CallStateInfo,
    lastTransition: String,
    registrationStates: Map<String, RegistrationState>,
    onShowDiagnostic: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "🔧 Debug Info",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Last transition: $lastTransition",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Call ID: ${callState.callId ?: "N/A"}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Direction: ${callState.direction?.name ?: "N/A"}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "Registered accounts: ${registrationStates.count { it.value == RegistrationState.OK }}",
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onShowDiagnostic,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = "Show Full Diagnostic",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Función auxiliar para formatear duración
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

// CORREGIDO: ModernDialPad con manejo mejorado de estados
@Composable
fun ModernDialPad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onCallClick: () -> Unit,
    enabled: Boolean,
    hasNumber: Boolean,
    isRegistrationInProgress: Boolean
) {
    val dialPadButtons = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Grid de números con animaciones
            dialPadButtons.forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { colIndex, (digit, letters) ->
                        AnimatedDialButton(
                            digit = digit,
                            letters = letters,
                            onClick = { onDigitClick(digit) },
                            enabled = true, // Los dígitos siempre están habilitados
                            animationDelay = (rowIndex * 3 + colIndex) * 50L,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botones de acción con diseño mejorado
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Backspace button
                ActionButton(
                    onClick = onBackspaceClick,
                    enabled = hasNumber,
                    isSecondary = true
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Call button con estado mejorado
                ActionButton(
                    onClick = onCallClick,
                    enabled = enabled && hasNumber,
                    isSecondary = false,
                    isLoading = isRegistrationInProgress
                ) {
                    if (isRegistrationInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedDialButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    enabled: Boolean,
    animationDelay: Long,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    LaunchedEffect(Unit) {
        delay(animationDelay)
        isVisible = true
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// CORREGIDO: ActionButton con estado de loading
@Composable
fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSecondary: Boolean,
    isLoading: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundColor = if (enabled) {
        if (isSecondary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }

    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}