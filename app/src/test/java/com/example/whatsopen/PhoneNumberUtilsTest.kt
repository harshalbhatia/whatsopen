package com.example.whatsopen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    // --- parsePhoneNumbers tests ---

    @Test
    fun `parsePhoneNumbers returns single valid number`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+919876543210")
        assertEquals(listOf("+919876543210"), result)
    }

    @Test
    fun `parsePhoneNumbers splits comma-separated numbers`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+919876543210,+14155551234")
        assertEquals(listOf("+919876543210", "+14155551234"), result)
    }

    @Test
    fun `parsePhoneNumbers splits newline-separated numbers`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+919876543210\n+14155551234")
        assertEquals(listOf("+919876543210", "+14155551234"), result)
    }

    @Test
    fun `parsePhoneNumbers splits space-separated numbers`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+919876543210 +14155551234")
        assertEquals(listOf("+919876543210", "+14155551234"), result)
    }

    @Test
    fun `parsePhoneNumbers handles mixed separators`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+919876543210,+14155551234\n+447700900000")
        assertEquals(3, result.size)
    }

    @Test
    fun `parsePhoneNumbers strips special characters`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+1(415)555-1234")
        assertEquals(listOf("+14155551234"), result)
    }

    @Test
    fun `parsePhoneNumbers filters numbers shorter than 7 digits`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("12345,+919876543210")
        assertEquals(listOf("+919876543210"), result)
    }

    @Test
    fun `parsePhoneNumbers keeps numbers with exactly 7 digits`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("1234567")
        assertEquals(listOf("1234567"), result)
    }

    @Test
    fun `parsePhoneNumbers returns empty list for empty input`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePhoneNumbers returns empty list for whitespace-only input`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePhoneNumbers preserves plus prefix`() {
        val result = PhoneNumberUtils.parsePhoneNumbers("+14155551234")
        assertTrue(result.first().startsWith("+"))
    }

    // --- splitPhoneNumber tests ---

    @Test
    fun `splitPhoneNumber splits international number with country code`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("+919876543210")
        assertEquals("+91", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber returns empty prefix for short number`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("9876543")
        assertEquals("", countryCode)
        assertEquals("9876543", localNumber)
    }

    @Test
    fun `splitPhoneNumber returns empty prefix for number without plus`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("919876543210")
        assertEquals("", countryCode)
        assertEquals("919876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber returns empty prefix for exactly 10 digits`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("9876543210")
        assertEquals("", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber handles 1-digit country code`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("+14155551234")
        assertEquals("+1", countryCode)
        assertEquals("4155551234", localNumber)
    }

    @Test
    fun `splitPhoneNumber handles 3-digit country code`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("+9669876543210")
        assertEquals("+966", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber trims whitespace`() {
        val (countryCode, localNumber) = PhoneNumberUtils.splitPhoneNumber("  +919876543210  ")
        assertEquals("+91", countryCode)
        assertEquals("9876543210", localNumber)
    }
}
