package com.harshalbhatia.whatsopen

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class DefaultRegionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val telephony = shadowOf(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
    private val originalLocale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `prefers network country`() {
        telephony.setNetworkCountryIso("in")
        telephony.setSimCountryIso("gb")
        Locale.setDefault(Locale.US)
        assertEquals("IN", DefaultRegion.get(context))
    }

    @Test
    fun `falls back to SIM country`() {
        telephony.setNetworkCountryIso("")
        telephony.setSimCountryIso("gb")
        Locale.setDefault(Locale.US)
        assertEquals("GB", DefaultRegion.get(context))
    }

    @Test
    fun `falls back to locale country`() {
        telephony.setNetworkCountryIso("")
        telephony.setSimCountryIso("")
        Locale.setDefault(Locale.GERMANY)
        assertEquals("DE", DefaultRegion.get(context))
    }

    @Test
    fun `returns null when nothing is known`() {
        telephony.setNetworkCountryIso("")
        telephony.setSimCountryIso("")
        Locale.setDefault(Locale.ENGLISH)
        assertNull(DefaultRegion.get(context))
    }

    @Test
    fun `uppercases without the default locale's rules`() {
        // Turkish uppercases "i" to dotted "İ"; country codes must not.
        Locale.setDefault(Locale("tr", "TR"))
        assertEquals("IN", DefaultRegion.cleanIso("in"))
        assertEquals("IN", DefaultRegion.firstValid(null, "in"))
    }

    @Test
    fun `skips values that aren't two-letter codes`() {
        assertEquals("MX", DefaultRegion.firstValid(null, " ", "419", "mx"))
        assertNull(DefaultRegion.cleanIso("USA"))
        assertNull(DefaultRegion.cleanIso("1A"))
        assertEquals("US", DefaultRegion.cleanIso(" us "))
    }
}
