package com.ahmedalsinan.dumbcallerid

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

object PhoneNumbers {
    // "+966500000000" -> "+966 50 000 0000", for display only; lookups use the number as received.
    fun format(context: Context, number: String): String {
        val country = context.getSystemService(TelephonyManager::class.java).networkCountryIso
            .ifEmpty { Locale.getDefault().country }.uppercase(Locale.ROOT)
        return PhoneNumberUtils.formatNumber(number, country) ?: number
    }
}
