package com.eddyslarez.siptest.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siptest.viewmodel.SipViewModel
import com.eddyslarez.siptest.viewmodel.getDisplayText
import com.eddyslarez.siptest.viewmodel.isCallActive
import kotlinx.coroutines.delay

@Composable
fun DialerScreen(
    sipViewModel: SipViewModel
) {
    val uiState by sipViewModel.uiState.collectAsState()
    val registrationState by sipViewModel.registrationState.collectAsState()
    val callState by sipViewModel.callState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Permite desplazamiento si el contenido es largo
        ) {
            // Estado de la llamada
            CallStatusCard(
                callState = callState,
                message = uiState.callMessage
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            // Teclado numérico
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
                enabled = registrationState == RegistrationState.OK && !callState.isCallActive(),
                hasNumber = uiState.dialedNumber.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Estado de registro
            if (registrationState != RegistrationState.OK) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Please register a SIP account first",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

    }
}

@Composable
fun CallStatusCard(
    callState: CallState,
    message: String
) {
    val (containerColor, contentColor, icon) = when (callState) {
        CallState.CONNECTED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "📞"
        )

        CallState.CALLING, CallState.RINGING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "📱"
        )

        CallState.INCOMING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "📲"
        )

        CallState.ERROR, CallState.ENDED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "❌"
        )

        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "📴"
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
            Column {
                Text(
                    text = callState.getDisplayText(),
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        color = contentColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ModernDialPad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onCallClick: () -> Unit,
    enabled: Boolean,
    hasNumber: Boolean
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
                            enabled = enabled,
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
                    enabled = enabled && hasNumber,
                    isSecondary = true
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Call button
                ActionButton(
                    onClick = onCallClick,
                    enabled = enabled && hasNumber,
                    isSecondary = false
                ) {
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
            .size(42.dp),
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

@Composable
fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSecondary: Boolean,
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
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}