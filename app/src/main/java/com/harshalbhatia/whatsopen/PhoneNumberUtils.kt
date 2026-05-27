package com.harshalbhatia.whatsopen

object PhoneNumberUtils {

    fun parsePhoneNumbers(input: String): List<String> {
        return input.split("\n", ",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("[^0-9+]"), "") }
            .filter { it.length >= 7 }
    }

    /**
     * Splits a phone number into (countryCode, localNumber).
     * Matches the longest country code prefix present in COUNTRY_DATA.
     * Falls back to ("", trimmed) if no prefix matches or input is too short.
     */
    fun splitPhoneNumber(number: String): Pair<String, String> {
        val trimmed = number.trim()
        val digits = trimmed.replace(Regex("[^0-9]"), "")
        if (!trimmed.startsWith("+") || digits.length < 7) {
            return Pair("", trimmed)
        }
        for (length in 3 downTo 1) {
            if (digits.length <= length) continue
            val candidate = digits.substring(0, length)
            if (COUNTRY_DATA.containsKey(candidate)) {
                return Pair("+$candidate", digits.substring(length))
            }
        }
        return Pair("", trimmed)
    }
}
