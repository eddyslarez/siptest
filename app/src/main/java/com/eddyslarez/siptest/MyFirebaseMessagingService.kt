package com.eddyslarez.siptest

import android.annotation.SuppressLint
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.utils.generateId
import com.eddyslarez.siplibrary.utils.log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// En tu servicio de FCM
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        log.d("push") { "Datos FCM recibidos: $data" }

        if (data["type"] == "call" && data.containsKey("sipName")) {
            val phoneNumber = data["incomingPhoneNumber"] ?: return
            val sipName = data["sipName"] ?: return
            val callId = data["callId"] ?: generateId()

            log.d("push") {
                "Llamada entrante detectada: phoneNumber=$phoneNumber, sipName=$sipName, callId=$callId"
            }

            // Notificar a la librería con datos específicos
            EddysSipLibrary.getInstance().onPushNotificationReceived(data)
        }
    }
}
