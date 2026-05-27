package com.harshalbhatia.whatsopen

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncomingIntentParserTest {

    @Test
    fun `returns null for null intent`() {
        assertNull(IncomingIntentParser.extractPhoneNumber(null))
    }

    @Test
    fun `extracts number from tel ACTION_VIEW`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:+14155551234"))
        assertEquals("+14155551234", IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `extracts number from tel ACTION_DIAL`() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:919876543210"))
        assertEquals("919876543210", IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `strips formatting from tel uri`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:+1-415-555-1234"))
        assertEquals("+14155551234", IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `returns null for non-tel scheme on ACTION_VIEW`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        assertNull(IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `returns null for too short tel number`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:12345"))
        assertNull(IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `extracts number from ACTION_SEND text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Call me at +1 415-555-1234 thanks")
        }
        assertEquals("+14155551234", IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `extracts number without plus from shared text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "9876543210")
        }
        assertEquals("9876543210", IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `returns null for shared text without numbers`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "hello world")
        }
        assertNull(IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `returns null for empty shared text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "")
        }
        assertNull(IncomingIntentParser.extractPhoneNumber(intent))
    }

    @Test
    fun `returns null for unrelated action`() {
        val intent = Intent(Intent.ACTION_MAIN)
        assertNull(IncomingIntentParser.extractPhoneNumber(intent))
    }
}
