package com.eddyslarez.siptest

import android.app.Application
import com.eddyslarez.siplibrary.EddysSipLibrary


class SipTestApplication : Application() {

    val sipLibrary by lazy { EddysSipLibrary.getInstance() }

    override fun onCreate() {
        super.onCreate()

        // Inicializar la biblioteca SIP
        initializeSipLibrary()
    }

    private fun initializeSipLibrary() {
        val config = EddysSipLibrary.SipConfig(
            defaultDomain = "mcn.ru",
            webSocketUrl = "wss://webrtc.mcn.ru:35060/",
            userAgent = "SipTestApp/1.0",
            enableLogs = true,
            enableAutoReconnect = true,
            pingIntervalMs = 30000L
        )

        sipLibrary.initialize(
            application = this,
            config = config
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        sipLibrary.dispose()
    }
}