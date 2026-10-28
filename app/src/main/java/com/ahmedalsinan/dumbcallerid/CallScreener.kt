package com.ahmedalsinan.dumbcallerid

import android.telecom.Call
import android.telecom.CallScreeningService

// Android calls this for each incoming call from a number that isn't in the user's contacts, once the app
// holds the caller ID role (see Setup). It replaces reading the call log, which Google Play restricts.
class CallScreener : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        // Never block or silence: the call rings as usual, the app only shows the popup.
        respondToCall(details, CallResponse.Builder().build())
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

        val number = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        var outcome = decideCall(number, Prefs.isEnabled(this), CallOverlay.canShow(this))
        if (outcome == RecentCalls.Outcome.POPUP && number != null && !CallOverlay.show(this, number)) {
            outcome = RecentCalls.Outcome.POPUP_BLOCKED
        }
        RecentCalls.record(this, number, outcome)
    }
}

// What to do with an incoming call, decided before the popup is attempted.
fun decideCall(number: String?, enabled: Boolean, canShowPopup: Boolean): RecentCalls.Outcome = when {
    number == null -> RecentCalls.Outcome.NO_NUMBER
    !enabled -> RecentCalls.Outcome.DISABLED
    !canShowPopup -> RecentCalls.Outcome.POPUP_BLOCKED
    else -> RecentCalls.Outcome.POPUP
}
