package com.ahmedalsinan.dumbcallerid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

// Removes the popup when the call ends (hung up, missed or declined). Showing it is CallScreener's job.
// A missed call can still be looked up from the main screen.
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) == TelephonyManager.EXTRA_STATE_IDLE) {
            CallOverlay.dismiss()
        }
    }
}
