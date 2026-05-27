package com.harshalbhatia.whatsopen

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingShareTest {

    @After
    fun tearDown() {
        PendingShare.text = null
    }

    @Test
    fun `consume returns null when nothing stored`() {
        assertNull(PendingShare.consume())
    }

    @Test
    fun `consume returns the stored value`() {
        PendingShare.text = "Hi from +14155551234"
        assertEquals("Hi from +14155551234", PendingShare.consume())
    }

    @Test
    fun `consume clears the stored value`() {
        PendingShare.text = "once"
        PendingShare.consume()
        assertNull(PendingShare.consume())
    }

    @Test
    fun `consume of empty string still clears`() {
        PendingShare.text = ""
        assertEquals("", PendingShare.consume())
        assertNull(PendingShare.text)
    }
}
