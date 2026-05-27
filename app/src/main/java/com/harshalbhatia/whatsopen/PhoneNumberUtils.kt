package com.harshalbhatia.whatsopen

object PhoneNumberUtils {

    /**
     * Parses a string containing one or more phone numbers separated by
     * newlines, commas, or spaces. Returns a list of cleaned phone numbers
     * that are at least 7 digits long.
     */
    fun parsePhoneNumbers(input: String): List<String> {
        return input.split("\n", ",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("[^0-9+]"), "") }
            .filter { it.length >= 7 }
    }

    /**
     * Splits a phone number into a country code prefix and a local number.
     * Returns a Pair of (countryCode, localNumber).
     *
     * If the number starts with "+" and is longer than 10 digits,
     * the prefix (everything before the last 10 digits) is treated as
     * the country code. Otherwise the full number is returned as the
     * local number with an empty country code.
     */
    fun splitPhoneNumber(number: String): Pair<String, String> {
        val trimmed = number.trim()
        if (trimmed.startsWith("+") && trimmed.length > 11) {
            val localNumber = trimmed.substring(trimmed.length - 10)
            val countryCode = trimmed.substring(0, trimmed.length - 10)
            return Pair(countryCode, localNumber)
        }
        return Pair("", trimmed)
    }
}
