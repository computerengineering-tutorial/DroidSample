package com.example.droidsample.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service required by Android OS for default SMS apps to handle quick text responses
 * when rejecting incoming phone calls (ACTION_RESPOND_VIA_MESSAGE).
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
