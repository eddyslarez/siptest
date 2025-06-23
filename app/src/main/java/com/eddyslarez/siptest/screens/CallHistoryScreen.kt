package com.eddyslarez.siptest.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eddyslarez.siplibrary.data.models.CallDirections
import com.eddyslarez.siplibrary.data.models.CallLog
import com.eddyslarez.siplibrary.data.models.CallTypes
import com.eddyslarez.siptest.viewmodel.SipViewModel

@Composable
fun CallHistoryScreen(
    sipViewModel: SipViewModel
) {
    // Nota: En tu biblioteca real, necesitarías exponer el call history
    // Por ahora, simularemos algunos datos para mostrar la UI
    val callHistory = remember {
        mutableStateListOf(
            createSampleCallLog("555-1234", CallTypes.SUCCESS, CallDirections.OUTGOING),
            createSampleCallLog("555-5678", CallTypes.MISSED, CallDirections.INCOMING),
            createSampleCallLog("555-9999", CallTypes.DECLINED, CallDirections.INCOMING),
            createSampleCallLog("555-1111", CallTypes.SUCCESS, CallDirections.INCOMING)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Estadísticas del historial
        CallStatisticsCard(callHistory)

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de llamadas
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Calls",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )

                    TextButton(
                        onClick = { callHistory.clear() }
                    ) {
                        Text("Clear All")
                    }
                }

                if (callHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No call history",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        items(callHistory) { callLog ->
                            CallHistoryItem(
                                callLog = callLog,
                                onCallClick = {
                                    // Llamar de vuelta al número
                                    sipViewModel.makeCall(callLog.to)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallStatisticsCard(
    callHistory: List<CallLog>
) {
    val totalCalls = callHistory.size
    val missedCalls = callHistory.count { it.callType == CallTypes.MISSED }
    val successfulCalls = callHistory.count { it.callType == CallTypes.SUCCESS }
    val declinedCalls = callHistory.count { it.callType == CallTypes.DECLINED }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Call Statistics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Total",
                    value = totalCalls,
                    icon = Icons.Default.Call,
                    color = MaterialTheme.colorScheme.primary
                )

                StatisticItem(
                    label = "Missed",
                    value = missedCalls,
                    icon = Icons.Default.CallReceived,
                    color = MaterialTheme.colorScheme.error
                )

                StatisticItem(
                    label = "Success",
                    value = successfulCalls,
                    icon = Icons.Default.CallMade,
                    color = MaterialTheme.colorScheme.secondary
                )

                StatisticItem(
                    label = "Declined",
                    value = declinedCalls,
                    icon = Icons.Default.CallEnd,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun StatisticItem(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CallHistoryItem(
    callLog: CallLog,
    onCallClick: () -> Unit
) {
    val (icon, iconColor) = when (callLog.callType) {
        CallTypes.SUCCESS -> {
            if (callLog.direction == CallDirections.INCOMING) {
                Icons.Default.CallReceived to MaterialTheme.colorScheme.secondary
            } else {
                Icons.Default.CallMade to MaterialTheme.colorScheme.primary
            }
        }
        CallTypes.MISSED -> Icons.Default.CallReceived to MaterialTheme.colorScheme.error
        CallTypes.DECLINED -> Icons.Default.CallEnd to MaterialTheme.colorScheme.tertiary
        CallTypes.ABORTED -> Icons.Default.CallEnd to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = callLog.callType.name,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (callLog.direction == CallDirections.INCOMING) callLog.from else callLog.to,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = callLog.formattedStartDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (callLog.duration > 0) {
                    Text(
                        text = " • ${formatDuration(callLog.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(
            onClick = onCallClick
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call back",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatDuration(durationSeconds: Int): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun createSampleCallLog(
    number: String,
    type: CallTypes,
    direction: CallDirections
): CallLog {
    return CallLog(
        id = System.currentTimeMillis().toString(),
        direction = direction,
        to = if (direction == CallDirections.OUTGOING) number else "1000",
        formattedTo = if (direction == CallDirections.OUTGOING) number else "1000",
        from = if (direction == CallDirections.INCOMING) number else "1000",
        formattedFrom = if (direction == CallDirections.INCOMING) number else "1000",
        contact = null,
        formattedStartDate = "Today 10:30",
        duration = if (type == CallTypes.SUCCESS) (30..300).random() else 0,
        callType = type,
        localAddress = "1000@mcn.ru"
    )
}