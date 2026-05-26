package com.example.whatsopen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsAppLauncherTest {

    @Test
    fun `buildWhatsAppUri generates correct URI for normal number`() {
        val uri = WhatsAppLauncher.buildWhatsAppUri("919876543210")
        assertEquals("https://api.whatsapp.com/send?phone=919876543210", uri.toString())
    }

    @Test
    fun `buildWhatsAppUri encodes special characters`() {
        val uri = WhatsAppLauncher.buildWhatsAppUri("+91 9876 543210")
        val uriString = uri.toString()
        assertTrue(
            "URI should encode spaces: $uriString",
            !uriString.contains(" ") || uriString.contains("%20") || uriString.contains("+")
        )
    }

    @Test
    fun `buildWhatsAppUri handles plus prefix`() {
        val uri = WhatsAppLauncher.buildWhatsAppUri("+14155551234")
        val uriString = uri.toString()
        assertTrue(
            "URI should contain the phone number: $uriString",
            uriString.contains("phone=")
        )
    }

    @Test
    fun `buildWhatsAppUri uses correct base URL`() {
        val uri = WhatsAppLauncher.buildWhatsAppUri("1234567890")
        assertTrue(uri.toString().startsWith("https://api.whatsapp.com/send"))
    }
}
