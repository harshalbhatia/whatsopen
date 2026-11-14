package com.harshalbhatia.whatsopen

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** The region numbers without a country code are assumed to belong to. Needs no permission. */
object DefaultRegion {

    /**
     * The device's region as an uppercase ISO 3166 code ("US"), from the mobile network,
     * then the SIM, then the system locale. Null if none of them gives a usable code.
     */
    fun get(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return firstValid(
            telephony?.let { runCatching { it.networkCountryIso }.getOrNull() },
            telephony?.let { runCatching { it.simCountryIso }.getOrNull() },
            Locale.getDefault().country,
        )
    }

    /** The first candidate that [cleanIso] accepts. */
    fun firstValid(vararg candidates: String?): String? = candidates.firstNotNullOfOrNull(::cleanIso)

    /**
     * "us" becomes "US". Null for anything that isn't two letters: blank, a UN M.49 code
     * like "419", or null. Uppercases with Locale.ROOT, so "in" stays "IN" on Turkish devices.
     */
    fun cleanIso(iso: String?): String? =
        iso?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
}
