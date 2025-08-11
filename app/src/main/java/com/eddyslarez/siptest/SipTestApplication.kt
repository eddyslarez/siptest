package com.eddyslarez.siptest

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.data.models.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import android.util.Log
import com.eddyslarez.siplibrary.data.models.PushMode
import com.eddyslarez.siplibrary.data.models.PushModeConfig
import com.eddyslarez.siplibrary.data.models.PushModeStrategy
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class SipTestApplication : Application() {

    val sipLibrary by lazy { EddysSipLibrary.getInstance() }

    private val sipAccounts = listOf(
//        SipAccount("90544000", "qsulxIRyGiajP664", "sip.spb.mcn.ru"),
        SipAccount("90544008", "9yeWXimVD1ABErCs", "sip.mcn.ru")
    )

    private var currentAccountIndex = 0
    private val registrationTimeout = 30_000L // 30 segundos timeout por cuenta
    private var registrationJob: Job? = null

    companion object {
        private const val TAG = "CuentasRegistro"
        private const val TAG1 = "CuentasRPush"
    }


    override fun onCreate() {
        super.onCreate()

        // Inicializar la biblioteca SIP con configuración de Push Mode
        initializeSipLibrary()

        // Configurar listeners
        setupRegistrationListener()
        setupPushModeListener() // ✅ NUEVO

        // Configurar lifecycle observer para Push Mode automático
        setupAppLifecycleObserver() // ✅ NUEVO

        // Iniciar el registro secuencial
        startSequentialRegistration()
    }

    fun getToken(onTokenReceived: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                onTokenReceived(null)
                return@addOnCompleteListener
            }

            // Token obtenido correctamente
            val token = task.result
            onTokenReceived(token)
        }
    }

    private fun initializeSipLibrary() {

        val pushModeConfig = PushModeConfig(
            strategy = PushModeStrategy.AUTOMATIC,
            autoTransitionDelay = 5000L,
            forceReregisterOnIncomingCall = true,
            returnToPushAfterCallEnd = true,
            enablePushNotifications = true
        )

        val config = EddysSipLibrary.SipConfig(
            defaultDomain = "mcn.ru",
            webSocketUrl = "wss://webrtc.mcn.ru:35060/",
            userAgent = "SipTestApp/1.0",
            enableLogs = true,
            enableAutoReconnect = true,
            pingIntervalMs = 30000L,
            pushModeConfig = pushModeConfig
        )

        sipLibrary.initialize(
            application = this,
            config = config,
            enableDatabase = true
        )
    }

    private fun setupPushModeListener() {

        CoroutineScope(Dispatchers.Main).launch {
            sipLibrary.getPushModeStateFlow().collect { pushState ->
                Log.i(TAG, "🔄 Push Mode cambió: ${pushState.currentMode} (${pushState.reason})")

                when (pushState.currentMode) {
                    PushMode.PUSH -> {
                        Log.i(
                            TAG1,
                            "📱 Aplicación en modo PUSH - ${pushState.accountsInPushMode.size} cuentas"
                        )

                    }

                    PushMode.FOREGROUND -> {
                        Log.i(TAG1, "🖥️ Aplicación en modo FOREGROUND")
                    }

                    PushMode.TRANSITIONING -> {
                        Log.i(TAG1, "⏳ Transicionando entre modos...")
                    }
                }
            }
        }
    }

    // ✅ NUEVO: Observer del lifecycle de la aplicación
    private fun setupAppLifecycleObserver() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var activityCount = 0

            override fun onActivityStarted(activity: Activity) {
                activityCount++
                if (activityCount == 1) {
                    // App pasó a foreground
                    Log.d(TAG, "🖥️ App pasó a FOREGROUND")
                    onAppForegrounded()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                activityCount--
                if (activityCount == 0) {
                    // App pasó a background
                    Log.d(TAG, "📱 App pasó a BACKGROUND")
                    onAppBackgrounded()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    // ✅ NUEVO: Manejar cuando la app pasa a background
    private fun onAppBackgrounded() {
        // El PushModeManager automáticamente manejará la transición a push mode
        // después del delay configurado (5 segundos)
        Log.i(TAG, "📱 App en background - Push Mode Manager iniciará transición automática")
    }

    // ✅ NUEVO: Manejar cuando la app pasa a foreground
    private fun onAppForegrounded() {
        // El PushModeManager automáticamente cancelará cualquier transición pendiente
        // y cambiará a foreground mode
        Log.i(TAG, "🖥️ App en foreground - Push Mode Manager cancelará transición a push")
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

        Log.i(
            TAG,
            "🔄 Registrando cuenta ${currentAccountIndex + 1}/${sipAccounts.size}: ${account.username}@${account.domain}"
        )

        // Cancelar job anterior si existe
        registrationJob?.cancel()

        // Crear nuevo job con timeout
        registrationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Obtener token FCM
                val pushToken = getFirebaseToken()
                if (pushToken == null) {
                    Log.e(TAG, "❌ No se pudo obtener el token FCM. Saltando registro.")
                    proceedToNextAccount()
                    return@launch
                }

                // Registrar la cuenta con el token
                sipLibrary.registerAccount(
                    username = account.username,
                    password = account.password,
                    domain = account.domain,
                    pushToken = pushToken
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
                Log.e(
                    TAG,
                    "💥 Error registrando ${account.username}@${account.domain}: ${e.message}"
                )
                proceedToNextAccount()
            }
        }
    }


    suspend fun getFirebaseToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e("FCM", "Error al obtener el token FCM", e)
            null
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