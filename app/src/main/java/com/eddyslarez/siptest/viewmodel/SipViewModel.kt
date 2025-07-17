package com.eddyslarez.siptest.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallErrorReason
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.CallStateInfo
import com.eddyslarez.siplibrary.data.models.RegistrationState
import com.eddyslarez.siplibrary.data.models.SipErrorMapper
import com.eddyslarez.siplibrary.utils.log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SipViewModel(
    private val sipLibrary: EddysSipLibrary
) : ViewModel() {
  val  TAG= "sipvoewmodel"
    private val _uiState = MutableStateFlow(SipUiState())
    val uiState: StateFlow<SipUiState> = _uiState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()
    // NUEVO: Estados de audio
    private val _isRecordingSentAudio = MutableStateFlow(false)
    val isRecordingSentAudio: StateFlow<Boolean> = _isRecordingSentAudio.asStateFlow()

    private val _isRecordingReceivedAudio = MutableStateFlow(false)
    val isRecordingReceivedAudio: StateFlow<Boolean> = _isRecordingReceivedAudio.asStateFlow()

    private val _isPlayingInputFile = MutableStateFlow(false)
    val isPlayingInputFile: StateFlow<Boolean> = _isPlayingInputFile.asStateFlow()

    private val _isPlayingOutputFile = MutableStateFlow(false)
    val isPlayingOutputFile: StateFlow<Boolean> = _isPlayingOutputFile.asStateFlow()
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

    // Historial de estados para debugging
    val callStateHistory: StateFlow<List<CallStateInfo>> = sipLibrary.getCallStateFlow()
        .map { currentState ->
            sipLibrary.getCallStateHistory()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        setupSipListeners()
        observeCallStates()
        observeRegistrationStates()   // ⬅️ nuevo

    }
    /** Actualiza _registrationState cuando lleguen cambios de la librería. */
    private fun observeRegistrationStates() = viewModelScope.launch {
        registrationStates.collect { allStates ->
            // Si ya sabemos qué usuario/dominio está usando la UI, úsalo:
            val key = "${_uiState.value.registeredUsername}@${_uiState.value.registeredDomain}"
            _registrationState.value = allStates[key] ?: RegistrationState.NONE
        }
    }
    private fun setupSipListeners() {
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

            // OPTIMIZADO: Listener unificado para estados de llamada
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

                // Manejar estados específicos
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

    fun updateCallMessage(message: String){
        log.d("callmessage" ,{ message })
    }
    // NUEVO: Funciones de grabación
    fun startRecordingSentAudio() {
        viewModelScope.launch {
            try {
                if (sipLibrary.startRecordingSentAudio()) {
                    _isRecordingSentAudio.value = true
                    updateCallMessage("🎙️ Grabando audio enviado...")
                } else {
                    updateCallMessage("❌ Error al iniciar grabación de audio enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting sent audio recording", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun stopRecordingSentAudio() {
        viewModelScope.launch {
            try {
                val filePath = sipLibrary.stopRecordingSentAudio()
                _isRecordingSentAudio.value = false
                if (filePath != null) {
                    updateCallMessage("✅ Audio enviado guardado:")
                } else {
                    updateCallMessage("⚠️ No se pudo guardar el audio enviado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping sent audio recording", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun startRecordingReceivedAudio() {
        viewModelScope.launch {
            try {
                if (sipLibrary.startRecordingReceivedAudio()) {
                    _isRecordingReceivedAudio.value = true
                    updateCallMessage("🎙️ Grabando audio recibido...")
                } else {
                    updateCallMessage("❌ Error al iniciar grabación de audio recibido")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting received audio recording", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun stopRecordingReceivedAudio() {
        viewModelScope.launch {
            try {
                val filePath = sipLibrary.stopRecordingReceivedAudio()
                _isRecordingReceivedAudio.value = false
                if (true) {
                    updateCallMessage("✅ Audio recibido guardado: ")
                } else {
                    updateCallMessage("⚠️ No se pudo guardar el audio recibido")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping received audio recording", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    // NUEVO: Funciones de reproducción
    fun startPlayingInputAudioFile(filePath: String, loop: Boolean = false) {
        viewModelScope.launch {
            try {
                if (sipLibrary.startPlayingInputAudioFile(filePath, loop)) {
                    _isPlayingInputFile.value = true
                    updateCallMessage("🔊 Reproduciendo archivo de entrada: ")
                } else {
                    updateCallMessage("❌ Error al reproducir archivo de entrada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting input audio file playback", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun stopPlayingInputAudioFile() {
        viewModelScope.launch {
            try {
                if (sipLibrary.stopPlayingInputAudioFile()) {
                    _isPlayingInputFile.value = false
                    updateCallMessage("⏹️ Detenida reproducción de entrada, volviendo al micrófono")
                } else {
                    updateCallMessage("❌ Error al detener reproducción de entrada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping input audio file playback", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun startPlayingOutputAudioFile(filePath: String, loop: Boolean = false) {
        viewModelScope.launch {
            try {

                if (sipLibrary.startPlayingOutputAudioFile(filePath, loop)) {
                    _isPlayingOutputFile.value = true
                    updateCallMessage("🔊 Reproduciendo archivo de salida: ${File(filePath).name}")
                } else {
                    updateCallMessage("❌ Error al reproducir archivo de salida")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting output audio file playback", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun stopPlayingOutputAudioFile() {
        viewModelScope.launch {
            try {
                if (sipLibrary.stopPlayingOutputAudioFile()) {
                    _isPlayingOutputFile.value = false
                    updateCallMessage("⏹️ Detenida reproducción de salida, volviendo al audio recibido")
                } else {
                    updateCallMessage("❌ Error al detener reproducción de salida")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping output audio file playback", e)
                updateCallMessage("❌ Error: ${e.message}")
            }
        }
    }

    // NUEVO: Funciones de gestión de archivos
    fun getRecordedAudioFiles(): List<File> {
        return try {
            sipLibrary.getRecordedAudioFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recorded audio files", e)
            emptyList()
        }
    }

    fun deleteRecordedAudioFile(filePath: String): Boolean {
        return try {
            sipLibrary.deleteRecordedAudioFile(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recorded audio file", e)
            false
        }
    }

    fun getAudioFileDuration(filePath: String): Long {
        return try {
            sipLibrary.getAudioFileDuration(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio file duration", e)
            0L
        }
    }

    fun getCurrentInputAudioFile(): String? {
        return try {
            sipLibrary.getCurrentInputAudioFile()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current input audio file", e)
            null
        }
    }

    fun getCurrentOutputAudioFile(): String? {
        return try {
            sipLibrary.getCurrentOutputAudioFile()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current output audio file", e)
            null
        }
    }

    fun showRecordedFiles() {
        // Implementar navegación a pantalla de archivos grabados
        val files = getRecordedAudioFiles()
        Log.d(TAG, "Recorded files: ${files.map { it.name }}")
        updateCallMessage("📁 ${files.size} archivos grabados disponibles")
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
    val allAccountsRegistered: Boolean = false
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

