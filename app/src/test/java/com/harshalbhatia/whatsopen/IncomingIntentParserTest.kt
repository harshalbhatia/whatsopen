package com.harshalbhatia.whatsopen

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncomingIntentParserTest {

    private fun send(text: CharSequence) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun processText(text: CharSequence) = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
    }

    @Test
    fun `returns null for null intent`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(null, "US"))
    }

    // --- tel: links ---

    @Test
    fun `extracts number from tel ACTION_VIEW`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:+14155551234"))
        assertEquals("+14155551234", IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `extracts national number from tel ACTION_DIAL using region`() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:09876543210"))
        assertEquals("+919876543210", IncomingIntentParser.extractPhoneNumber(intent, "IN"))
    }

    @Test
    fun `reads tel national number as US number, not as a country code`() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:4155552671"))
        assertEquals("+14155552671", IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `strips formatting from tel uri`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:+1-415-555-1234"))
        assertEquals("+14155551234", IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `decodes encoded tel uri and drops parameters and pauses`() {
        assertEquals(
            "+14155552671",
            IncomingIntentParser.extractPhoneNumber(
                Intent(Intent.ACTION_VIEW, Uri.parse("tel:%2B1%20415%20555%202671;ext=12")), "GB"
            )
        )
        assertEquals(
            "+14155552671",
            IncomingIntentParser.extractPhoneNumber(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+14155552671,,123")), "GB")
        )
    }

    @Test
    fun `returns null for non-tel scheme on ACTION_VIEW`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        assertNull(IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `returns null for too short tel number`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:12345"))
        assertNull(IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `returns null for tel number longer than 15 digits`() {
        assertNull(
            IncomingIntentParser.extractPhoneNumber(Intent(Intent.ACTION_VIEW, Uri.parse("tel:+1234567890123456")), "US")
        )
        assertNull(
            IncomingIntentParser.extractPhoneNumber(Intent(Intent.ACTION_VIEW, Uri.parse("tel:12345678901234567890")), "US")
        )
    }

    // --- shared text ---

    @Test
    fun `extracts number from ACTION_SEND text`() {
        val intent = send("Call me at +1 415-555-1234 thanks")
        assertEquals("+14155551234", IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `extracts spaced international number from shared text`() {
        assertEquals("+919876543210", IncomingIntentParser.extractPhoneNumber(send("+91 98765 43210"), "US"))
    }

    @Test
    fun `extracts national number from shared text using region`() {
        assertEquals("+919876543210", IncomingIntentParser.extractPhoneNumber(send("9876543210"), "IN"))
    }

    @Test
    fun `reads styled shared text`() {
        val styled = SpannableString("Call +44 7911 123456").apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 5, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        assertEquals("+447911123456", IncomingIntentParser.extractPhoneNumber(send(styled), "US"))
    }

    @Test
    fun `returns null for order number and date`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(send("Order #12345678 shipped"), "US"))
        assertNull(IncomingIntentParser.extractPhoneNumber(send("2026-11-14"), "US"))
        assertNull(IncomingIntentParser.extractPhoneNumber(send("Order #12345678 shipped on 2026-11-14"), "US"))
    }

    @Test
    fun `returns null when shared text has two different numbers`() {
        val intent = send("Me: +919876543210, office: +14155552671")
        assertNull(IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `counts the same number written twice once`() {
        val intent = send("Call +1 415 555 2671 (that's (415) 555-2671)")
        assertEquals("+14155552671", IncomingIntentParser.extractPhoneNumber(intent, "US"))
    }

    @Test
    fun `returns null for shared text without numbers`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(send("hello world"), "US"))
    }

    @Test
    fun `returns null for empty shared text`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(send(""), "US"))
    }

    @Test
    fun `returns null for unrelated action`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(Intent(Intent.ACTION_MAIN), "US"))
    }

    // --- selected text (PROCESS_TEXT) ---

    @Test
    fun `extracts number from ACTION_PROCESS_TEXT`() {
        assertEquals(
            "+447911123456",
            IncomingIntentParser.extractPhoneNumber(processText("07911 123456"), "GB")
        )
    }

    @Test
    fun `returns null for ACTION_PROCESS_TEXT with two numbers`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(processText("+919876543210 +14155552671"), "US"))
    }

    // --- sharedText ---

    @Test
    fun `sharedText returns SEND and PROCESS_TEXT text`() {
        assertEquals("hello", IncomingIntentParser.sharedText(send("hello")))
        assertEquals("hello", IncomingIntentParser.sharedText(processText("hello")))
    }

    @Test
    fun `sharedText returns null for blank text or other actions`() {
        assertNull(IncomingIntentParser.sharedText(send("  ")))
        assertNull(IncomingIntentParser.sharedText(Intent(Intent.ACTION_VIEW, Uri.parse("tel:+14155552671"))))
        assertNull(IncomingIntentParser.sharedText(null))
    }

    @Test
    fun `sharedText is capped at 4096 characters`() {
        val text = IncomingIntentParser.sharedText(send("a".repeat(10_000)))
        assertEquals(PhoneNumbers.MAX_TEXT_CHARS, text!!.length)
    }
}
