package com.eddyslarez.siptest.viewmodel


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallErrorReason
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.CallStateInfo
import com.eddyslarez.siplibrary.data.models.PushMode
import com.eddyslarez.siplibrary.data.models.PushModeState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.models.SipErrorMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SipViewModel(
    private val sipLibrary: EddysSipLibrary
) : ViewModel() {

    private val _uiState = MutableStateFlow(SipUiState())
    val uiState: StateFlow<SipUiState> = _uiState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    val callState: StateFlow<CallStateInfo> = sipLibrary.getCallStateFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly,
            CallStateInfo(
                state = CallState.IDLE,
                previousState = null,
                timestamp = System.currentTimeMillis()
            )
        )
    /** Último estado de la cuenta que se esté registrando / usando.            */
    private val _registrationState = MutableStateFlow(RegistrationState.NONE)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()
    // Estados de registro multi-cuenta
    val registrationStates: StateFlow<Map<String, RegistrationState>> = sipLibrary.getRegistrationStatesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())


    // ✅ NUEVO: Estados de Push Mode
    val pushModeState: StateFlow<PushModeState> = sipLibrary.getPushModeStateFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly,
            PushModeState(
                currentMode = PushMode.FOREGROUND,
                previousMode = null,
                timestamp = System.currentTimeMillis(),
                reason = "Initial state"
            )
        )
    // Historial de estados para debugging
    val callStateHistory: StateFlow<List<CallStateInfo>> = sipLibrary.getCallStateFlow()
        .map { currentState ->
            sipLibrary.getCallStateHistory()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        setupSipListeners()
        observeCallStates()
        observeRegistrationStates()
        observePushModeStates() // ✅ NUEVO


    }

    /** ✅ NUEVO: Observar cambios de modo push */
    private fun observePushModeStates() = viewModelScope.launch {
        pushModeState.collect { pushState ->
            Log.d("SipViewModel", "Push mode changed: ${pushState.currentMode} (${pushState.reason})")

            _uiState.update { currentUiState ->
                currentUiState.copy(
                    pushModeStatus = "Push Mode: ${pushState.currentMode.name}",
                    pushModeReason = pushState.reason,
                    isInPushMode = pushState.currentMode == PushMode.PUSH,
                    accountsInPushMode = pushState.accountsInPushMode.size
                )
            }
        }
    }
    /** Actualiza _registrationState cuando lleguen cambios de la librería. */
    private fun observeRegistrationStates() = viewModelScope.launch {
        registrationStates.collect { allStates ->
            // Si ya sabemos qué usuario/dominio está usando la UI, úsalo:
            val key = "${_uiState.value.registeredUsername}@${_uiState.value.registeredDomain}"
            _registrationState.value = allStates[key] ?: RegistrationState.NONE
        }
    }

    /**
     * Cambia manualmente a modo push
     */
    fun switchToPushMode() {
        viewModelScope.launch {
            try {
                sipLibrary.switchToPushMode()
                Log.d("SipViewModel", "Switched to push mode manually")
            } catch (e: Exception) {
                Log.e("SipViewModel", "Error switching to push mode: ${e.message}")
                _uiState.update {
                    it.copy(callMessage = "Error switching to push mode: ${e.message}")
                }
            }
        }
    }
    /**
     * Cambia manualmente a modo foreground
     */
    fun switchToForegroundMode() {
        viewModelScope.launch {
            try {
                sipLibrary.switchToForegroundMode()
                Log.d("SipViewModel", "Switched to foreground mode manually")
            } catch (e: Exception) {
                Log.e("SipViewModel", "Error switching to foreground mode: ${e.message}")
                _uiState.update {
                    it.copy(callMessage = "Error switching to foreground mode: ${e.message}")
                }
            }
        }
    }
    private fun setupSipListeners() {

        // Listener para estado de red
        sipLibrary.setNetworkStatusListener(object : EddysSipLibrary.NetworkStatusListener {
            override fun onNetworkConnected(networkType: String, hasInternet: Boolean) {

                Log.d("SipListener", "Red conectada: $networkType (Internet: $hasInternet)")

            }

            override fun onNetworkDisconnected() {
                Log.d("SipListener", "Red desconectada")

            }

            override fun onNetworkChanged(oldNetworkType: String, newNetworkType: String) {
                Log.d("SipListener", "Red cambió: $oldNetworkType → $newNetworkType")

            }

            override fun onInternetConnectivityChanged(hasInternet: Boolean) {
                Log.d("SipListener", "Conectividad internet: $hasInternet")

            }
        })

        // Listener para reconexión automática
        sipLibrary.setAutoReconnectionListener(object : EddysSipLibrary.AutoReconnectionListener {
            override fun onReconnectionStarted(accountKey: String, reason: String) {
                Log.d("SipListener", "Iniciando reconexión para $accountKey: $reason")

            }

            override fun onReconnectionSuccess(accountKey: String, attempts: Int) {
                Log.d("SipListener", "Reconexión exitosa para $accountKey (intentos: $attempts)")

            }

            override fun onReconnectionFailed(accountKey: String, attempts: Int, error: String) {
                Log.d("SipListener","Reconexión fallida para $accountKey: $error")

            }

            override fun onReconnectionProgress(accountKey: String, attempt: Int, maxAttempts: Int) {
                Log.d("SipListener", "Reconectando $accountKey: $attempt/$maxAttempts")

            }
        })
        // Listener principal para eventos SIP
        sipLibrary.addSipEventListener(object : EddysSipLibrary.SipEventListener {
            override fun onRegistrationStateChanged(state: RegistrationState, username: String, domain: String) {
                _registrationState.value = state
                Log.d("SipListener", "onRegistrationStateChanged: $username@$domain -> ${state.name}")
                _uiState.update {
                    it.copy(
                        registrationMessage = "Registration: ${state.name} ($username@$domain)",
                        isRegistered = state == RegistrationState.OK
                    )
                }
            }

            override fun onCallStateChanged(stateInfo: CallStateInfo) {
                Log.d("SipListener", "onCallStateChanged: ${stateInfo.state.name}")

                val message = buildCallMessage(stateInfo)
                _uiState.update {
                    it.copy(
                        callMessage = message,
                        detailedCallMessage = message,
                        lastStateTransition = "${stateInfo.previousState?.name ?: "NONE"} → ${stateInfo.state.name}",
                        hasCallError = stateInfo.hasError(),
                        errorReason = if (stateInfo.hasError()) stateInfo.errorReason.name else null
                    )
                }

                handleStateChange(stateInfo)
            }

            override fun onIncomingCall(callInfo: EddysSipLibrary.IncomingCallInfo) {
                Log.d("SipListener", "onIncomingCall from: ${callInfo.callerNumber}")
                _uiState.update {
                    it.copy(
                        callMessage = "Incoming call from ${callInfo.callerNumber}",
                        incomingCall = callInfo
                    )
                }
            }

            override fun onCallConnected(callInfo: EddysSipLibrary.CallInfo) {
                Log.d("SipListener", "onCallConnected with: ${callInfo.phoneNumber}")
                _uiState.update {
                    it.copy(
                        callMessage = "Call connected with ${callInfo.phoneNumber}",
                        currentCall = callInfo
                    )
                }
            }

            override fun onCallEnded(callInfo: EddysSipLibrary.CallInfo, reason: EddysSipLibrary.CallEndReason) {
                Log.d("SipListener", "onCallEnded: ${reason.name}, callInfo: $callInfo")
                _uiState.update {
                    it.copy(
                        callMessage = "Call ended: ${reason.name}",
                        currentCall = null,
                        incomingCall = null,
                        hasCallError = false,
                        errorReason = null
                    )
                }
            }

            override fun onCallFailed(error: String, callInfo: EddysSipLibrary.CallInfo?) {
                Log.d("SipListener", "onCallFailed: $error, callInfo: $callInfo")
                _uiState.update {
                    it.copy(
                        callMessage = "Call failed: $error",
                        hasCallError = true,
                        errorReason = "FAILED"
                    )
                }
            }
        })

        // Listener específico para llamadas con estados detallados
        sipLibrary.setCallListener(object : EddysSipLibrary.CallListener {
            override fun onCallInitiated(callInfo: EddysSipLibrary.CallInfo) {
                Log.d("CallListener", "onCallInitiated to: ${callInfo.phoneNumber}")
                _uiState.update {
                    it.copy(
                        callMessage = "Calling ${callInfo.phoneNumber}...",
                        currentCall = callInfo
                    )
                }
            }

            override fun onCallRinging(callInfo: EddysSipLibrary.CallInfo) {
                Log.d("CallListener", "onCallRinging: ${callInfo.phoneNumber}")
                _uiState.update {
                    it.copy(
                        callMessage = "Ringing ${callInfo.phoneNumber}..."
                    )
                }
            }

            override fun onCallConnected(callInfo: EddysSipLibrary.CallInfo) {
                Log.d("CallListener", "onCallConnected: ${callInfo.phoneNumber}")
            }

            override fun onCallHeld(callInfo: EddysSipLibrary.CallInfo) {
                Log.d("CallListener", "onCallHeld")
                _uiState.update {
                    it.copy(
                        callMessage = "Call on hold",
                        currentCall = callInfo
                    )
                }
            }

            override fun onCallResumed(callInfo: EddysSipLibrary.CallInfo) {
                Log.d("CallListener", "onCallResumed")
                _uiState.update {
                    it.copy(
                        callMessage = "Call resumed",
                        currentCall = callInfo
                    )
                }
            }

            override fun onCallEnded(callInfo: EddysSipLibrary.CallInfo, reason: EddysSipLibrary.CallEndReason) {
                Log.d("CallListener", "onCallEnded: ${reason.name}")
            }

            override fun onCallTransferred(callInfo: EddysSipLibrary.CallInfo, transferTo: String) {
                Log.d("CallListener", "onCallTransferred to: $transferTo")
            }

            override fun onMuteStateChanged(isMuted: Boolean, callInfo: EddysSipLibrary.CallInfo) {
                Log.d("CallListener", "onMuteStateChanged: $isMuted")
                _uiState.update {
                    it.copy(
                        currentCall = callInfo
                    )
                }
            }

            // OPTIMIZADO: Listener unificado para estados detallados específicos de llamada
            override fun onCallStateChanged(stateInfo: CallStateInfo) {
                Log.d("CallListener", "onCallStateChanged: ${stateInfo.state}")

                // Aquí puedes manejar lógica específica de UI para cada estado
                when (stateInfo.state) {
                    CallState.OUTGOING_INIT -> {
                        _uiState.update { it.copy(callMessage = "Iniciando llamada...") }
                    }
                    CallState.OUTGOING_PROGRESS -> {
                        _uiState.update { it.copy(callMessage = "Estableciendo conexión...") }
                    }
                    CallState.OUTGOING_RINGING -> {
                        _uiState.update { it.copy(callMessage = "Sonando...") }
                    }
                    CallState.INCOMING_RECEIVED -> {
                        _uiState.update { it.copy(callMessage = "Llamada entrante") }
                    }
                    CallState.CONNECTED -> {
                        _uiState.update { it.copy(callMessage = "Conectado") }
                    }
                    CallState.STREAMS_RUNNING -> {
                        _uiState.update { it.copy(callMessage = "Audio activo") }
                    }
                    CallState.PAUSING -> {
                        _uiState.update { it.copy(callMessage = "Pausando llamada...") }
                    }
                    CallState.PAUSED -> {
                        _uiState.update { it.copy(callMessage = "Llamada en espera") }
                    }
                    CallState.RESUMING -> {
                        _uiState.update { it.copy(callMessage = "Reanudando llamada...") }
                    }
                    CallState.ENDING -> {
                        _uiState.update { it.copy(callMessage = "Finalizando llamada...") }
                    }
                    CallState.ENDED -> {
                        _uiState.update { it.copy(callMessage = "Llamada finalizada") }
                    }
                    CallState.ERROR -> {
                        val errorMsg = SipErrorMapper.getErrorDescription(stateInfo.errorReason)
                        _uiState.update {
                            it.copy(
                                callMessage = "Error: $errorMsg",
                                hasCallError = true,
                                errorReason = stateInfo.errorReason.name
                            )
                        }
                    }
                    else -> {}
                }
            }
        })
    }

    /**
     * Simula que se recibió una notificación push (para testing)
     */
    fun simulatePushNotificationReceived() {
        viewModelScope.launch {
            try {
                sipLibrary.onPushNotificationReceived()
                Log.d("SipViewModel", "Simulated push notification received")
            } catch (e: Exception) {
                Log.e("SipViewModel", "Error simulating push notification: ${e.message}")
            }
        }
    }

    /**
     * Actualiza el token push
     */
    fun updatePushToken(token: String, provider: String = "fcm") {
        viewModelScope.launch {
            try {
                sipLibrary.updatePushToken(token, provider)
                Log.d("SipViewModel", "Push token updated successfully")
                _uiState.update {
                    it.copy(registrationMessage = "Push token updated")
                }
            } catch (e: Exception) {
                Log.e("SipViewModel", "Error updating push token: ${e.message}")
                _uiState.update {
                    it.copy(registrationMessage = "Error updating push token: ${e.message}")
                }
            }
        }
    }

    /**
     * Obtiene información de diagnóstico del push mode
     */
    fun getPushModeDiagnostic(): String {
        return buildString {
            val currentState = pushModeState.value
            appendLine("=== PUSH MODE DIAGNOSTIC ===")
            appendLine("Current Mode: ${currentState.currentMode}")
            appendLine("Previous Mode: ${currentState.previousMode}")
            appendLine("Reason: ${currentState.reason}")
            appendLine("Timestamp: ${currentState.timestamp}")
            appendLine("Accounts in Push: ${currentState.accountsInPushMode.size}")
            appendLine("Was in Push Before Call: ${currentState.wasInPushBeforeCall}")

            appendLine("\n--- Library Push State ---")
            appendLine("Is in Push Mode: ${sipLibrary.isInPushMode()}")
            appendLine("Current Push Mode: ${sipLibrary.getCurrentPushMode()}")
        }
    }
    // OPTIMIZADO: Observar estados unificados para lógica adicional
    private fun observeCallStates() {
        viewModelScope.launch {
            callState.collect { stateInfo ->
                // Lógica adicional basada en estados
                when (stateInfo.state) {
                    CallState.STREAMS_RUNNING -> {
                        // Iniciar timer de duración de llamada
                        startCallDurationTimer()
                    }
                    CallState.ENDED, CallState.ERROR -> {
                        // Detener timer de duración
                        stopCallDurationTimer()
                    }
                    else -> {}
                }
            }
        }

        // Observar estados de registro multi-cuenta
        viewModelScope.launch {
            registrationStates.collect { states ->
                val registeredCount = states.values.count { it == RegistrationState.OK }
                val totalCount = states.size

                _uiState.update {
                    it.copy(
                        multiAccountStatus = "Cuentas registradas: $registeredCount/$totalCount",
                        allAccountsRegistered = registeredCount == totalCount && totalCount > 0
                    )
                }
            }
        }
    }

    // OPTIMIZADO: Construir mensaje del estado
    private fun buildCallMessage(stateInfo: CallStateInfo): String {
        val baseMessage = when (stateInfo.state) {
            CallState.IDLE -> "Sin llamadas"
            CallState.OUTGOING_INIT -> "Iniciando llamada saliente"
            CallState.OUTGOING_PROGRESS -> "Progreso de llamada (${stateInfo.sipCode})"
            CallState.OUTGOING_RINGING -> "Teléfono sonando"
            CallState.INCOMING_RECEIVED -> "Llamada entrante recibida"
            CallState.CONNECTED -> "Llamada conectada"
            CallState.STREAMS_RUNNING -> "Audio en curso"
            CallState.PAUSING -> "Pausando..."
            CallState.PAUSED -> "En espera"
            CallState.RESUMING -> "Reanudando..."
            CallState.ENDING -> "Finalizando..."
            CallState.ENDED -> "Llamada terminada"
            CallState.ERROR -> "Error: ${SipErrorMapper.getErrorDescription(stateInfo.errorReason)}"
        }

        return if (stateInfo.sipCode != null) {
            "$baseMessage (${stateInfo.sipCode})"
        } else {
            baseMessage
        }
    }

    // OPTIMIZADO: Manejar cambios de estado específicos
    private fun handleStateChange(stateInfo: CallStateInfo) {
        when (stateInfo.state) {
            CallState.ERROR -> {
                // Manejar errores específicos
                when (stateInfo.errorReason) {
                    CallErrorReason.BUSY -> {
                        _uiState.update { it.copy(callMessage = "Línea ocupada") }
                    }
                    CallErrorReason.NO_ANSWER -> {
                        _uiState.update { it.copy(callMessage = "Sin respuesta") }
                    }
                    CallErrorReason.REJECTED -> {
                        _uiState.update { it.copy(callMessage = "Llamada rechazada") }
                    }
                    CallErrorReason.NETWORK_ERROR -> {
                        _uiState.update { it.copy(callMessage = "Error de red") }
                    }
                    else -> {
                        _uiState.update { it.copy(callMessage = "Error desconocido") }
                    }
                }
            }
            CallState.OUTGOING_RINGING -> {
                // Iniciar sonido de ringback si es necesario
                Log.d("SipViewModel", "Call is ringing - could start ringback tone")
            }
            CallState.STREAMS_RUNNING -> {
                // Audio está fluyendo - actualizar UI
                Log.d("SipViewModel", "Audio streams are running")
            }
            else -> {}
        }
    }

    // Timer de duración de llamada
    private var callDurationTimer: Job? = null
    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private fun startCallDurationTimer() {
        stopCallDurationTimer()
        callDurationTimer = viewModelScope.launch {
            var duration = 0L
            while (isActive) {
                _callDuration.value = duration
                delay(1000)
                duration += 1000
            }
        }
    }

    private fun stopCallDurationTimer() {
        callDurationTimer?.cancel()
        callDurationTimer = null
        _callDuration.value = 0L
    }

    // OPTIMIZADO: Métodos para obtener información
    fun getCurrentCallState(): CallStateInfo {
        return sipLibrary.getCurrentCallState()
    }

    fun getCallStateHistory(): List<CallStateInfo> {
        return sipLibrary.getCallStateHistory()
    }

    fun clearCallStateHistory() {
        sipLibrary.clearCallStateHistory()
    }

    fun getSystemDiagnostic(): String {
        return buildString {
            appendLine("=== SYSTEM DIAGNOSTIC ===")
            appendLine(sipLibrary.diagnoseListeners())
            appendLine("\n=== CALL STATE HISTORY ===")
            getCallStateHistory().takeLast(10).forEach { state ->
                appendLine("${state.timestamp}: ${state.previousState} -> ${state.state}")
                if (state.hasError()) {
                    appendLine("  Error: ${state.errorReason} (${state.sipCode})")
                }
            }
        }
    }

    // Métodos existentes...
    fun onPermissionsGranted() {
        _permissionsGranted.value = true
    }

    fun onPermissionsDenied() {
        _permissionsGranted.value = false
        _uiState.update { it.copy(
            registrationMessage = "Permissions required for SIP functionality"
        )}
    }

    fun registerAccount(username: String, password: String, domain: String, pushToken: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    registrationMessage = "Registering...",
                    isRegistering = true
                )}

                sipLibrary.registerAccount(
                    username = username,
                    password = password,
                    domain = domain.ifEmpty { null },
                    pushToken = pushToken.ifEmpty { null }
                )

                _uiState.update { it.copy(
                    registeredUsername = username,
                    registeredDomain = domain,
                    isRegistering = false
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    registrationMessage = "Registration failed: ${e.message}",
                    isRegistering = false
                )}
            }
        }
    }

    fun makeCall(phoneNumber: String) {
        viewModelScope.launch {
            try {
                sipLibrary.makeCall(phoneNumber)
                _uiState.update { it.copy(
                    dialedNumber = phoneNumber
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    callMessage = "Failed to make call: ${e.message}"
                )}
            }
        }
    }

    fun acceptCall() {
        sipLibrary.setOpenAIEnabled( true)
        viewModelScope.launch {
            sipLibrary.acceptCall()
        }
    }

    fun declineCall() {
        viewModelScope.launch {
            sipLibrary.declineCall()
        }
    }

    fun endCall() {
        viewModelScope.launch {
            sipLibrary.endCall()
        }
    }

    fun holdCall() {
        viewModelScope.launch {
            sipLibrary.holdCall()
        }
    }

    fun resumeCall() {
        viewModelScope.launch {
            sipLibrary.resumeCall()
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            sipLibrary.toggleMute()
        }
    }

    fun sendDtmf(digit: Char) {
        viewModelScope.launch {
            val success = sipLibrary.sendDtmf(digit)
            _uiState.update { it.copy(
                callMessage = if (success) "DTMF sent: $digit" else "Failed to send DTMF: $digit"
            )}
        }
    }

    fun updateDialedNumber(number: String) {
        _uiState.update { it.copy(dialedNumber = number) }
    }

    fun clearDialedNumber() {
        _uiState.update { it.copy(dialedNumber = "") }
    }

    class Factory(private val sipLibrary: EddysSipLibrary) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SipViewModel::class.java)) {
                return SipViewModel(sipLibrary) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// ACTUALIZADO: Estado de UI con nuevos campos
data class SipUiState(
    val registrationMessage: String = "Not registered",
    val callMessage: String = "No active calls",
    val dialedNumber: String = "",
    val registeredUsername: String = "",
    val registeredDomain: String = "",
    val isRegistered: Boolean = false,
    val isRegistering: Boolean = false,
    val currentCall: EddysSipLibrary.CallInfo? = null,
    val incomingCall: EddysSipLibrary.IncomingCallInfo? = null,

    // Campos para estados
    val detailedCallMessage: String = "Idle",
    val lastStateTransition: String = "",
    val hasCallError: Boolean = false,
    val errorReason: String? = null,
    val multiAccountStatus: String = "No accounts",
    val allAccountsRegistered: Boolean = false,

    // ✅ NUEVOS CAMPOS PARA PUSH MODE
    val pushModeStatus: String = "Push Mode: FOREGROUND",
    val pushModeReason: String = "Initial state",
    val isInPushMode: Boolean = false,
    val accountsInPushMode: Int = 0
)

// OPTIMIZADO: Extension functions para estados
fun CallState.isCallActive(): Boolean {
    return this in listOf(
        CallState.OUTGOING_INIT,
        CallState.OUTGOING_PROGRESS,
        CallState.OUTGOING_RINGING,
        CallState.INCOMING_RECEIVED,
        CallState.CONNECTED,
        CallState.STREAMS_RUNNING,
        CallState.PAUSING,
        CallState.PAUSED,
        CallState.RESUMING
    )

}

fun CallState.getDisplayText(): String {
    return when (this) {
        CallState.IDLE -> "Sin llamadas"
        CallState.OUTGOING_INIT -> "Iniciando..."
        CallState.OUTGOING_PROGRESS -> "Conectando..."
        CallState.OUTGOING_RINGING -> "Sonando..."
        CallState.INCOMING_RECEIVED -> "Llamada entrante"
        CallState.CONNECTED -> "Conectado"
        CallState.STREAMS_RUNNING -> "En llamada"
        CallState.PAUSING -> "Pausando..."
        CallState.PAUSED -> "En espera"
        CallState.RESUMING -> "Reanudando..."
        CallState.ENDING -> "Finalizando..."
        CallState.ENDED -> "Finalizada"
        CallState.ERROR -> "Error"
    }
}

