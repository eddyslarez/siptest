package com.eddyslarez.siptest

import android.app.Application
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import android.util.Log
import com.eddyslarez.siplibrary.data.services.audio.WebRtcManager

class SipTestApplication : Application() {

    val sipLibrary by lazy { EddysSipLibrary.getInstance() }

    private val sipAccounts = listOf(
        SipAccount("90544000", "", "sip.spb.mcn.ru"),
//        SipAccount("9054607", "GK94phfudf0Eq", "sip.f.cru")
    )

    private var currentAccountIndex = 0
    private val registrationTimeout = 30_000L // 30 segundos timeout por cuenta
    private var registrationJob: Job? = null

    companion object {
        private const val TAG = "CuentasRegistro"
    }

    override fun onCreate() {
        super.onCreate()

        // Inicializar la biblioteca SIP
        initializeSipLibrary()

        // Configurar el listener para manejar estados de registro
        setupRegistrationListener()

        // NUEVO: Configurar listener para eventos de audio
//        setupAudioEventListener()

        // Iniciar el registro secuencial
        startSequentialRegistration()
    }
//    // NUEVO: Configurar listener para eventos de audio
//    private fun setupAudioEventListener() {
//        sipLibrary.setAudioEventListener(object : EddysSipLibrary.AudioEventListener {
//            override fun onRecordingStateChanged(isRecording: Boolean, filePath: String?, isSentAudio: Boolean) {
//                val type = if (isSentAudio) "enviado" else "recibido"
//                if (isRecording) {
//                    Log.d(TAG, "🎙️ Iniciada grabación de audio $type")
//                } else {
//                    Log.d(TAG, "⏹️ Detenida grabación de audio $type: $filePath")
//                }
//            }
//
//            override fun onAudioFilePlaybackStateChanged(isPlaying: Boolean, filePath: String?, isInputAudio: Boolean) {
//                val type = if (isInputAudio) "entrada" else "salida"
//                if (isPlaying) {
//                    Log.d(TAG, "🔊 Iniciada reproducción de audio $type: $filePath")
//                } else {
//                    Log.d(TAG, "⏸️ Detenida reproducción de audio $type")
//                }
//            }
//        })
//    }
    private fun initializeSipLibrary() {
        val config = EddysSipLibrary.SipConfig(
            defaultDomain = "mcn.ru",
            webSocketUrl = "wss://webrtc.mcn.ru:35060/",
            userAgent = "SipTestApp/1.0",
            enableLogs = true,
            enableAutoReconnect = true,
            pingIntervalMs = 30000L,
            openAIApiKey="sk-proj-g9hb9",
            defaultTargetLanguage = "es",
            enableAutoTranslation = true,
            translationQuality= WebRtcManager.TranslationQuality.HIGH
        )

        sipLibrary.initialize(
            application = this,
            config = config
        )
    }

    private fun setupRegistrationListener() {
        sipLibrary.setRegistrationListener(object : EddysSipLibrary.RegistrationListener {
            override fun onRegistrationSuccessful(username: String, domain: String) {
                Log.d(TAG, "✅ Registro exitoso: $username@$domain")

                // Verificar si es la cuenta actual que estamos registrando
                val currentAccount = getCurrentAccount()
                if (currentAccount?.username == username && currentAccount.domain == domain) {
                    proceedToNextAccount()
                }
            }

            override fun onRegistrationFailed(username: String, domain: String, error: String) {
                Log.e(TAG, "❌ Registro fallido: $username@$domain - Error: $error")

                // Verificar si es la cuenta actual que estamos registrando
                val currentAccount = getCurrentAccount()
                if (currentAccount?.username == username && currentAccount.domain == domain) {
                    // Decidir si continuar con la siguiente cuenta o reintentar
                    // Por ahora, continuamos con la siguiente
                    proceedToNextAccount()
                }
            }

            override fun onUnregistered(username: String, domain: String) {
                Log.i(TAG, "📤 Desregistrado: $username@$domain")
            }

            override fun onRegistrationExpiring(username: String, domain: String, expiresIn: Long) {
                Log.w(TAG, "⏰ Registro expirando: $username@$domain en ${expiresIn}ms")
            }
        })
    }

    private fun startSequentialRegistration() {
        if (sipAccounts.isEmpty()) {
            Log.w(TAG, "No hay cuentas para registrar")
            return
        }

        currentAccountIndex = 0
        registerCurrentAccount()
    }

    private fun registerCurrentAccount() {
        val account = getCurrentAccount()
        if (account == null) {
            Log.i(TAG, "✅ Todos los registros completados")
            onAllRegistrationsCompleted()
            return
        }

        Log.i(TAG, "🔄 Registrando cuenta ${currentAccountIndex + 1}/${sipAccounts.size}: ${account.username}@${account.domain}")

        // Cancelar job anterior si existe
        registrationJob?.cancel()

        // Crear nuevo job con timeout
        registrationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Registrar la cuenta
                sipLibrary.registerAccount(
                    username = account.username,
                    password = account.password,
                    domain = account.domain
                )

                // Esperar con timeout
                delay(registrationTimeout)

                // Si llegamos aquí, significa que no recibimos respuesta en el tiempo esperado
                Log.w(TAG, "⏱️ Timeout esperando registro de ${account.username}@${account.domain}")
                proceedToNextAccount()

            } catch (e: CancellationException) {
                // Job cancelado, normal
                Log.d(TAG, "🚫 Registro cancelado para ${account.username}@${account.domain}")
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error registrando ${account.username}@${account.domain}: ${e.message}")
                proceedToNextAccount()
            }
        }
    }

    private fun proceedToNextAccount() {
        // Cancelar el job de timeout actual
        registrationJob?.cancel()

        // Avanzar al siguiente índice
        currentAccountIndex++

        // Pequeña pausa antes del siguiente registro
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000) // 1 segundo de pausa
            registerCurrentAccount()
        }
    }

    private fun getCurrentAccount(): SipAccount? {
        return if (currentAccountIndex < sipAccounts.size) {
            sipAccounts[currentAccountIndex]
        } else {
            null
        }
    }

    private fun onAllRegistrationsCompleted() {
        Log.i(TAG, "🎉 Proceso de registro completado para todas las cuentas")

        // Aquí puedes agregar lógica adicional una vez que todas las cuentas
        // hayan intentado registrarse

        // Por ejemplo, mostrar el estado final de todas las cuentas:
        showFinalRegistrationStatus()
    }

    private fun showFinalRegistrationStatus() {
        Log.i(TAG, "📊 Estado final de registros:")
        Log.i(TAG, "=" * 50)

        sipAccounts.forEach { account ->
            val state = sipLibrary.getRegistrationState(account.username, account.domain)
            val status = when (state) {
                RegistrationState.OK -> "✅ REGISTRADO"
                RegistrationState.FAILED -> "❌ FALLIDO"
                RegistrationState.PROGRESS -> "🔄 EN PROGRESO"
                RegistrationState.CLEARED -> "📤 DESREGISTRADO"
                else -> "❓ DESCONOCIDO"
            }
            Log.i(TAG, "${account.username}@${account.domain}: $status")
        }
        Log.i(TAG, "=" * 50)
    }

    override fun onTerminate() {
        super.onTerminate()

        // Cancelar cualquier job de registro pendiente
        registrationJob?.cancel()

        // Limpiar la biblioteca
        sipLibrary.dispose()
    }
}

data class SipAccount(
    val username: String,
    val password: String,
    val domain: String
)

// Extensión para repetir strings (como en Python)
private operator fun String.times(n: Int): String = this.repeat(n)