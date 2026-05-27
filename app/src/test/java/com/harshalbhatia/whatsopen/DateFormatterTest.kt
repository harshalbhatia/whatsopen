package com.harshalbhatia.whatsopen

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DateFormatterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now = 1_700_000_000_000L
    private val absoluteFormatter = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    @Test
    fun `returns absolute format when older than 7 days`() {
        val tenDaysAgo = now - TimeUnit.DAYS.toMillis(10)
        assertEquals(
            absoluteFormatter.format(Date(tenDaysAgo)),
            DateFormatter.format(context, tenDaysAgo, now)
        )
    }

    @Test
    fun `at exactly 7 days uses absolute format`() {
        val sevenDays = now - TimeUnit.DAYS.toMillis(7)
        assertEquals(
            absoluteFormatter.format(Date(sevenDays)),
            DateFormatter.format(context, sevenDays, now)
        )
    }

    @Test
    fun `under 7 days does not use absolute format`() {
        val twoHoursAgo = now - TimeUnit.HOURS.toMillis(2)
        val out = DateFormatter.format(context, twoHoursAgo, now)
        val absolute = absoluteFormatter.format(Date(twoHoursAgo))
        assertNotEquals("relative path must differ from absolute format", absolute, out)
        assertTrue("relative result must be non-empty", out.isNotBlank())
    }

    @Test
    fun `under 7 days returns non-empty result`() {
        val almostSeven = now - (TimeUnit.DAYS.toMillis(7) - 1)
        val out = DateFormatter.format(context, almostSeven, now)
        assertTrue(out.isNotBlank())
    }
}
