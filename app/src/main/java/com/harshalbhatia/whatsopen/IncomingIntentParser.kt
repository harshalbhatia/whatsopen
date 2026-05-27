package com.harshalbhatia.whatsopen

import android.content.Intent
import android.net.Uri

object IncomingIntentParser {

    fun extractPhoneNumber(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> fromTelUri(intent.data)
            Intent.ACTION_DIAL -> fromTelUri(intent.data)
            Intent.ACTION_SEND -> fromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
            else -> null
        }
    }

    private fun fromTelUri(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme?.lowercase() != "tel") return null
        val raw = uri.schemeSpecificPart ?: return null
        return cleanAndValidate(raw)
    }

    private fun fromSharedText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val candidate = Regex("\\+?[0-9 ()\\-]{7,}").find(text)?.value ?: return null
        return cleanAndValidate(candidate)
    }

    private fun cleanAndValidate(raw: String): String? {
        val hadPlus = raw.trimStart().startsWith("+")
        val digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.length < 7) return null
        return if (hadPlus) "+$digits" else digits
    }
}
