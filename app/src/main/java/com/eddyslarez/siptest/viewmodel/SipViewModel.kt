package com.eddyslarez.siptest.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.CallState
import com.eddyslarez.siplibrary.data.models.RegistrationState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SipViewModel(
    private val sipLibrary: EddysSipLibrary
) : ViewModel() {

    private val _uiState = MutableStateFlow(SipUiState())
    val uiState: StateFlow<SipUiState> = _uiState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    // Estados de la biblioteca SIP
    val callState: StateFlow<CallState> = sipLibrary.getCallStateFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, CallState.NONE)

    val registrationState: StateFlow<RegistrationState> = sipLibrary.getRegistrationStateFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RegistrationState.NONE)

    init {
        setupSipListeners()
    }

    private fun setupSipListeners() {
        // Listener principal para eventos SIP
        sipLibrary.addSipEventListener(object : EddysSipLibrary.SipEventListener {
            override fun onRegistrationStateChanged(state: RegistrationState, username: String, domain: String) {
                Log.d("SipListener", "onRegistrationStateChanged: $username@$domain -> ${state.name}")
                _uiState.update {
                    it.copy(
                        registrationMessage = "Registration: ${state.name}",
                        isRegistered = state == RegistrationState.OK
                    )
                }
            }

            override fun onCallStateChanged(state: CallState, callInfo: EddysSipLibrary.CallInfo?) {
                Log.d("SipListener", "onCallStateChanged: ${state.name}, callInfo: $callInfo")
                _uiState.update {
                    it.copy(
                        callMessage = "Call: ${state.name}",
                        currentCall = callInfo
                    )
                }
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
                        incomingCall = null
                    )
                }
            }

            override fun onCallFailed(error: String, callInfo: EddysSipLibrary.CallInfo?) {
                Log.d("SipListener", "onCallFailed: $error, callInfo: $callInfo")
                _uiState.update {
                    it.copy(
                        callMessage = "Call failed: $error"
                    )
                }
            }
        })

        // Listener específico para llamadas
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
                // Se maneja en el otro listener también
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
                // Se maneja en el otro listener también
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
        })
    }


    // Acciones del usuario
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
        // Implementar envío de DTMF a través de la biblioteca
        _uiState.update { it.copy(
            callMessage = "DTMF sent: $digit"
        )}
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

data class SipUiState(
    val registrationMessage: String = "Not registered",
    val callMessage: String = "No active calls",
    val dialedNumber: String = "",
    val registeredUsername: String = "",
    val registeredDomain: String = "",
    val isRegistered: Boolean = false,
    val isRegistering: Boolean = false,
    val currentCall: EddysSipLibrary.CallInfo? = null,
    val incomingCall: EddysSipLibrary.IncomingCallInfo? = null
)

// Extension functions para estados
fun CallState.isCallActive(): Boolean {
    return this in listOf(
        CallState.CALLING,
        CallState.RINGING,
        CallState.CONNECTED,
        CallState.INCOMING,
        CallState.ACCEPTING,
        CallState.HOLDING
    )
}

fun CallState.getDisplayText(): String {
    return when (this) {
        CallState.NONE -> "No Call"
        CallState.INCOMING -> "Incoming Call"
        CallState.OUTGOING -> "Outgoing Call"
        CallState.CALLING -> "Calling..."
        CallState.RINGING -> "Ringing..."
        CallState.CONNECTED -> "Connected"
        CallState.HOLDING -> "On Hold"
        CallState.ACCEPTING -> "Accepting..."
        CallState.ENDING -> "Ending..."
        CallState.ENDED -> "Call Ended"
        CallState.DECLINED -> "Declined"
        CallState.ERROR -> "Error"
        CallState.IDLE -> "Idle"
        CallState.DIALING -> "Dialing..."
        CallState.PAUSED -> "Call Paused"
        CallState.FAILED -> "Call Failed"
        CallState.CANCELLED -> "Call Cancelled"
        CallState.DECLINING -> "Declining Call..."
        CallState.RESUMING -> "Resuming Call..."
        CallState.INITIATING -> "Initiating Call..."
    }

}