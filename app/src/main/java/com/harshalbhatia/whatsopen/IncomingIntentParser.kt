package com.harshalbhatia.whatsopen

import android.content.Intent
import android.net.Uri

object IncomingIntentParser {

    /**
     * The number to open straight away, as E.164, or null.
     *
     * - tel: links (VIEW, DIAL) are numbers the user tapped on purpose, so any number that
     *   [PhoneNumbers.normalize] accepts is returned.
     * - Shared (SEND) or selected (PROCESS_TEXT) text gives a number only when it contains
     *   exactly one valid number (the same number twice counts once). Otherwise null, and the
     *   caller should let the user confirm using [sharedText].
     */
    fun extractPhoneNumber(intent: Intent?, defaultRegion: String?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_DIAL -> fromTelUri(intent.data, defaultRegion)
            Intent.ACTION_SEND, Intent.ACTION_PROCESS_TEXT ->
                sharedText(intent)?.let { PhoneNumbers.extract(it, defaultRegion).singleOrNull() }
            else -> null
        }
    }

    /**
     * The text of a SEND or PROCESS_TEXT intent, cut to [PhoneNumbers.MAX_TEXT_CHARS]
     * characters. Null for other actions or blank text.
     */
    fun sharedText(intent: Intent?): String? {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        } ?: return null
        return PhoneNumbers.capText(text).takeIf { it.isNotBlank() }
    }

    private fun fromTelUri(uri: Uri?, defaultRegion: String?): String? {
        if (uri?.scheme?.lowercase() != "tel") return null
        val raw = uri.schemeSpecificPart ?: return null
        // Drop URI parameters (";ext=", ";phone-context=") and dial pauses (",").
        val number = raw.substringBefore(';').substringBefore(',')
        return PhoneNumbers.normalize(number, defaultRegion)
    }
}
