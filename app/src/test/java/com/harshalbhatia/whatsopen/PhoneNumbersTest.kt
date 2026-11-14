package com.harshalbhatia.whatsopen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs on Robolectric because validation uses the platform's libphonenumber. */
@RunWith(RobolectricTestRunner::class)
class PhoneNumbersTest {

    // --- normalize: international input ---

    @Test
    fun `normalize keeps spaced plus number whole`() {
        assertEquals("+919876543210", PhoneNumbers.normalize("+91 98765 43210", "US"))
    }

    @Test
    fun `normalize accepts plus number that strict validation rejects`() {
        // 07700 900xxx is a reserved fictional UK range, invalid in libphonenumber.
        assertEquals("+447700900123", PhoneNumbers.normalize("+44 7700 900123", "US"))
    }

    @Test
    fun `normalize accepts fictional 555 number written with plus`() {
        assertEquals("+15551234567", PhoneNumbers.normalize("+1 555-123-4567", "US"))
    }

    @Test
    fun `normalize reads 00 as international prefix even where region dials 011`() {
        assertEquals("+442079460000", PhoneNumbers.normalize("0044 20 7946 0000", "US"))
    }

    @Test
    fun `normalize reads 00 as international prefix in a 00 region`() {
        assertEquals("+442079460000", PhoneNumbers.normalize("0044 20 7946 0000", "DE"))
    }

    @Test
    fun `normalize handles region international prefix 011`() {
        assertEquals("+442079460000", PhoneNumbers.normalize("011 44 20 7946 0000", "US"))
    }

    @Test
    fun `normalize drops trunk prefix after country code`() {
        assertEquals("+79161234567", PhoneNumbers.normalize("+7 8 916 123-45-67", "US"))
    }

    @Test
    fun `normalize drops bracketed trunk zero after country code`() {
        assertEquals("+442079460000", PhoneNumbers.normalize("+44 (0)20 7946 0000", "US"))
        assertEquals("+447700900123", PhoneNumbers.normalize("+44 (0) 7700 900123", "US"))
    }

    // --- normalize: national input ---

    @Test
    fun `normalize reads US national number with US region`() {
        assertEquals("+14155552671", PhoneNumbers.normalize("4155552671", "US"))
    }

    @Test
    fun `normalize reads bracketed US national number`() {
        assertEquals("+14155552671", PhoneNumbers.normalize("(415) 555-2671", "US"))
    }

    @Test
    fun `normalize reads UK national number with trunk zero`() {
        assertEquals("+447911123456", PhoneNumbers.normalize("07911 123456", "GB"))
    }

    @Test
    fun `normalize rejects national number in a range strict validation rejects`() {
        // Only "+" numbers get the lenient fallback; see the report on 07700 900123.
        assertNull(PhoneNumbers.normalize("07700 900123", "GB"))
    }

    @Test
    fun `normalize reads Russian national number with trunk 8`() {
        assertEquals("+79161234567", PhoneNumbers.normalize("8 916 123-45-67", "RU"))
    }

    @Test
    fun `normalize accepts lowercase region`() {
        assertEquals("+14155552671", PhoneNumbers.normalize("4155552671", "us"))
    }

    @Test
    fun `normalize rejects national number without region`() {
        assertNull(PhoneNumbers.normalize("4155552671", null))
        assertNull(PhoneNumbers.normalize("4155552671", ""))
    }

    @Test
    fun `normalize does not read national digits as a country code`() {
        // The old bug: 4155552671 opened +41 (Switzerland). In GB it's simply not valid.
        assertNull(PhoneNumbers.normalize("4155552671", "GB"))
    }

    @Test
    fun `normalize reads plus number without region`() {
        assertEquals("+919876543210", PhoneNumbers.normalize("+919876543210", null))
    }

    @Test
    fun `normalize reads country code written without plus`() {
        // wa.me links write numbers this way.
        assertEquals("+919876543210", PhoneNumbers.normalize("919876543210", "US"))
        assertEquals("+919876543210", PhoneNumbers.normalize("91 98765 43210", "GB"))
        assertEquals("+447911123456", PhoneNumbers.normalize("447911123456", "US"))
        assertEquals("+14155552671", PhoneNumbers.normalize("14155552671", "GB"))
        assertEquals("+919876543210", PhoneNumbers.normalize("919876543210", null))
    }

    @Test
    fun `normalize prefers national number over country code without plus`() {
        // Valid as a US number, so it isn't read as +41 (Switzerland).
        assertEquals("+14155552671", PhoneNumbers.normalize("4155552671", "US"))
        // In India 9876543210 is national; it must not become +98 (Iran).
        assertEquals("+919876543210", PhoneNumbers.normalize("9876543210", "IN"))
    }

    @Test
    fun `normalize does not read a trunk zero as a country code`() {
        assertNull(PhoneNumbers.normalize("0919876543210", "US"))
    }

    // --- normalize: Unicode ---

    @Test
    fun `normalize converts Arabic-Indic digits`() {
        assertEquals("+201001234567", PhoneNumbers.normalize("+٢٠ ١٠٠ ١٢٣ ٤٥٦٧", "EG"))
    }

    @Test
    fun `normalize converts Persian digits`() {
        assertEquals("+989123456789", PhoneNumbers.normalize("۰۹۱۲ ۳۴۵ ۶۷۸۹", "IR"))
    }

    @Test
    fun `normalize converts Devanagari digits`() {
        assertEquals("+919876543210", PhoneNumbers.normalize("+९१ ९८७६५ ४३२१०", "IN"))
    }

    @Test
    fun `normalize ignores bidi override and zero-width characters`() {
        val text = "‮+1 415​ 555‍ 2671‬⁦﻿"
        assertEquals("+14155552671", PhoneNumbers.normalize(text, "US"))
    }

    @Test
    fun `normalize maps non-breaking hyphens and spaces`() {
        assertEquals("+14155552671", PhoneNumbers.normalize("+1 415‑555‑2671", "US"))
    }

    // --- normalize: rejection ---

    @Test
    fun `normalize rejects more than 15 digits even with plus`() {
        assertNull(PhoneNumbers.normalize("+1234567890123456", "US"))
        assertNull(PhoneNumbers.normalize("+91 98765 43210 12345", "US"))
    }

    @Test
    fun `normalize rejects plus number with unassigned country code`() {
        assertNull(PhoneNumbers.normalize("+999 1234 5678", "US"))
    }

    @Test
    fun `normalize rejects plus number shorter than 8 digits`() {
        assertNull(PhoneNumbers.normalize("+44 12345", "US"))
    }

    @Test
    fun `normalize rejects text and blanks`() {
        assertNull(PhoneNumbers.normalize("", "US"))
        assertNull(PhoneNumbers.normalize("   ", "US"))
        assertNull(PhoneNumbers.normalize("hello", "US"))
        assertNull(PhoneNumbers.normalize("12345678", "US"))
    }

    @Test
    fun `normalize is idempotent`() {
        for (input in listOf("+919876543210", "+447700900123", "+15551234567", "+442079460000")) {
            assertEquals(input, PhoneNumbers.normalize(input, "US"))
            assertEquals(input, PhoneNumbers.normalize(input, "IN"))
        }
    }

    // --- extract ---

    @Test
    fun `extract finds single number`() {
        assertEquals(listOf("+919876543210"), PhoneNumbers.extract("+919876543210", "US"))
    }

    @Test
    fun `extract keeps spaced international number whole`() {
        assertEquals(listOf("+919876543210"), PhoneNumbers.extract("+91 98765 43210", "US"))
    }

    @Test
    fun `extract keeps plus 1 with dashes whole instead of reading +55`() {
        assertEquals(listOf("+15551234567"), PhoneNumbers.extract("+1 555-123-4567", "US"))
    }

    @Test
    fun `extract splits space-separated plus numbers`() {
        assertEquals(
            listOf("+919876543210", "+14155551234"),
            PhoneNumbers.extract("+919876543210 +14155551234", "US")
        )
    }

    @Test
    fun `extract splits space-separated national numbers`() {
        assertEquals(
            listOf("+14155552671", "+14155552672"),
            PhoneNumbers.extract("4155552671 4155552672", "US")
        )
    }

    @Test
    fun `extract splits comma, semicolon and newline separated numbers`() {
        assertEquals(
            listOf("+919876543210", "+14155551234", "+447911123456"),
            PhoneNumbers.extract("+919876543210,+14155551234;+44 7911 123456", "US")
        )
        assertEquals(
            listOf("+919876543210", "+14155551234"),
            PhoneNumbers.extract("+919876543210\n+14155551234", "US")
        )
    }

    @Test
    fun `extract treats comma as separator even between digits`() {
        assertEquals(listOf("+14155552671"), PhoneNumbers.extract("4155552671,2", "US"))
    }

    @Test
    fun `extract strips brackets and dashes`() {
        assertEquals(listOf("+14155551234"), PhoneNumbers.extract("+1(415)555-1234", "US"))
    }

    @Test
    fun `extract finds number inside a sentence`() {
        assertEquals(
            listOf("+14155552671"),
            PhoneNumbers.extract("Call me at (415) 555-2671 after 6.", "US")
        )
    }

    @Test
    fun `extract stops a number before trailing digit groups`() {
        assertEquals(
            listOf("+14155552671"),
            PhoneNumbers.extract("Call +1 415 555 2671 24 hours a day", "US")
        )
    }

    @Test
    fun `extract reads national number with default region`() {
        assertEquals(listOf("+447911123456"), PhoneNumbers.extract("Ring 07911 123456 today", "GB"))
    }

    @Test
    fun `extract handles 00 prefix`() {
        assertEquals(listOf("+442079460000"), PhoneNumbers.extract("Office: 0044 20 7946 0000", "US"))
    }

    @Test
    fun `extract counts repeated number once`() {
        assertEquals(
            listOf("+14155552671"),
            PhoneNumbers.extract("+1 415 555 2671, or (415) 555-2671", "US")
        )
    }

    @Test
    fun `extract reads country code written without plus`() {
        assertEquals(listOf("+919876543210"), PhoneNumbers.extract("WhatsApp me on 919876543210 thanks", "US"))
    }

    @Test
    fun `extract prefers national split over country code without plus`() {
        assertEquals(
            listOf("+14155552671", "+14155552672"),
            PhoneNumbers.extract("4155552671 4155552672", "US")
        )
    }

    @Test
    fun `extract ignores order numbers`() {
        assertTrue(PhoneNumbers.extract("Order #12345678 shipped", "US").isEmpty())
        assertTrue(PhoneNumbers.extract("Order #9876543210 shipped", "IN").isEmpty())
        assertTrue(PhoneNumbers.extract("Ref AB9876543210", "IN").isEmpty())
    }

    @Test
    fun `extract ignores dates`() {
        assertTrue(PhoneNumbers.extract("2026-11-14", "US").isEmpty())
        assertTrue(PhoneNumbers.extract("Delivered 14.11.2026", "DE").isEmpty())
    }

    @Test
    fun `extract ignores numbers inside links`() {
        assertTrue(PhoneNumbers.extract("https://shop.example/item/9876543210", "IN").isEmpty())
    }

    @Test
    fun `extract rejects more than 15 digits as one number`() {
        assertTrue(PhoneNumbers.extract("12345678901234567890", "US").isEmpty())
        assertTrue(PhoneNumbers.extract("+12345678901234567", "US").isEmpty())
        assertTrue(PhoneNumbers.extract("4111 1111 1111 1111", "US").isEmpty())
    }

    @Test
    fun `extract converts Arabic-Indic digits and ignores invisible characters`() {
        assertEquals(
            listOf("+201001234567"),
            PhoneNumbers.extract("رقمي ‏+٢٠ ١٠٠ ١٢٣ ٤٥٦٧‏ شكرا", "EG")
        )
    }

    @Test
    fun `extract handles right-to-left override around number`() {
        assertEquals(
            listOf("+14155552671"),
            PhoneNumbers.extract("call ‮+1 415 555 2671‬ now", "US")
        )
    }

    @Test
    fun `extract filters numbers shorter than 7 digits`() {
        assertEquals(listOf("+919876543210"), PhoneNumbers.extract("12345,+919876543210", "US"))
    }

    @Test
    fun `extract returns empty list for blank input`() {
        assertTrue(PhoneNumbers.extract("", "US").isEmpty())
        assertTrue(PhoneNumbers.extract("   ", "US").isEmpty())
        assertTrue(PhoneNumbers.extract("hello world", "US").isEmpty())
    }

    @Test
    fun `extract only searches the first 4096 characters`() {
        val padding = "x".repeat(PhoneNumbers.MAX_TEXT_CHARS)
        assertTrue(PhoneNumbers.extract("$padding +919876543210", "US").isEmpty())
        assertEquals(
            listOf("+919876543210"),
            PhoneNumbers.extract("+919876543210 $padding", "US")
        )
    }

    // --- sanitize ---

    @Test
    fun `sanitize converts digits and removes format characters`() {
        assertEquals("+1 415", PhoneNumbers.sanitize("‪＋١ ۴१5​‬"))
    }

    // --- splitPhoneNumber ---

    @Test
    fun `splitPhoneNumber splits international number with country code`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("+919876543210")
        assertEquals("+91", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber returns empty prefix for short number`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("9876543")
        assertEquals("", countryCode)
        assertEquals("9876543", localNumber)
    }

    @Test
    fun `splitPhoneNumber returns empty prefix for number without plus`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("919876543210")
        assertEquals("", countryCode)
        assertEquals("919876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber returns empty prefix for exactly 10 digits`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("9876543210")
        assertEquals("", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber handles 1-digit country code`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("+14155551234")
        assertEquals("+1", countryCode)
        assertEquals("4155551234", localNumber)
    }

    @Test
    fun `splitPhoneNumber handles 3-digit country code`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("+9669876543210")
        assertEquals("+966", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber trims whitespace`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("  +919876543210  ")
        assertEquals("+91", countryCode)
        assertEquals("9876543210", localNumber)
    }

    @Test
    fun `splitPhoneNumber prefers longer country code when prefix overlaps`() {
        // 212 (Morocco) and 20 (Egypt) both start with 2. Should pick 212.
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("+2121234567")
        assertEquals("+212", countryCode)
        assertEquals("1234567", localNumber)
    }

    @Test
    fun `splitPhoneNumber strips formatting before splitting`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("+1 (415) 555-1234")
        assertEquals("+1", countryCode)
        assertEquals("4155551234", localNumber)
    }

    @Test
    fun `splitPhoneNumber falls back when no known country code prefix`() {
        // 999 is not a real country code
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("+9991234567")
        assertEquals("", countryCode)
        assertEquals("+9991234567", localNumber)
    }

    @Test
    fun `splitPhoneNumber handles empty string`() {
        val (countryCode, localNumber) = PhoneNumbers.splitPhoneNumber("")
        assertEquals("", countryCode)
        assertEquals("", localNumber)
    }
}
