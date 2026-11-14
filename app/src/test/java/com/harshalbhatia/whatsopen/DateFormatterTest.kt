package com.harshalbhatia.whatsopen

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DateFormatterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var timeZone: TimeZone

    /** Tuesday 14 November 2023, 22:13:20 UTC. */
    private val now = 1_700_000_000_000L
    private val tenDaysAgo = now - TimeUnit.DAYS.toMillis(10)
    private val lastYear = now - TimeUnit.DAYS.toMillis(400)

    @Before
    fun setUp() {
        timeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(timeZone)
    }

    private fun format(timestamp: Long, now: Long = this.now, context: Context = this.context) =
        // Newer ICU data puts a narrow no-break space before AM/PM.
        DateFormatter.format(context, timestamp, now).replace(' ', ' ').replace(' ', ' ')

    private fun use24Hour(on: Boolean) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, if (on) "24" else "12")
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `older than a week shows the date and time, without this year`() {
        assertEquals("Nov 4, 10:13 PM", format(tenDaysAgo))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `older than a week in another year shows the year`() {
        assertEquals("Oct 10, 2022, 10:13 PM", format(lastYear))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `days ago in the previous year show the year`() {
        val newYear = 1_704_456_000_000L // 5 January 2024, 12:00 UTC
        val christmas = 1_703_505_600_000L // 25 December 2023, 12:00 UTC
        assertEquals("Dec 25, 2023, 12:00 PM", format(christmas, now = newYear))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `exactly 7 days is no longer relative`() {
        assertEquals("Nov 7, 10:13 PM", format(now - TimeUnit.DAYS.toMillis(7)))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `within a week is relative, with the time`() {
        assertEquals("2 hr. ago, 8:13 PM", format(now - TimeUnit.HOURS.toMillis(2)))
        assertEquals("Yesterday, 8:13 PM", format(now - TimeUnit.HOURS.toMillis(26)))
        assertEquals("3 days ago, 10:13 PM", format(now - TimeUnit.DAYS.toMillis(3)))
        assertEquals("7 days ago, 10:13 PM", format(now - TimeUnit.DAYS.toMillis(7) + 1))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `under a minute is Now, not 0 min ago`() {
        assertEquals("Now, 10:13 PM", format(now - TimeUnit.SECONDS.toMillis(20)))
        // A clock a few seconds out doesn't make it "In 0 min.".
        assertEquals("Now, 10:13 PM", format(now + TimeUnit.SECONDS.toMillis(5)))
        assertEquals("1 min. ago, 10:12 PM", format(now - TimeUnit.MINUTES.toMillis(1)))
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `Now is translated`() {
        assertEquals("Jetzt, 22:13", format(now - TimeUnit.SECONDS.toMillis(20)))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `24-hour setting applies to relative and absolute times in a 12-hour locale`() {
        use24Hour(true)
        assertEquals("2 hr. ago, 20:13", format(now - TimeUnit.HOURS.toMillis(2)))
        assertEquals("Nov 4, 22:13", format(tenDaysAgo))
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun `en-GB uses its own order and the 24-hour clock`() {
        assertEquals("4 Nov, 22:13", format(tenDaysAgo))
        assertEquals("10 Oct 2022, 22:13", format(lastYear))
        assertEquals("2 hr ago, 20:13", format(now - TimeUnit.HOURS.toMillis(2)))
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun `12-hour setting applies in a 24-hour locale`() {
        use24Hour(false)
        assertEquals("4 Nov, 10:13 pm", format(tenDaysAgo))
        assertEquals("2 hr ago, 8:13 pm", format(now - TimeUnit.HOURS.toMillis(2)))
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `German`() {
        assertEquals("4. Nov., 22:13", format(tenDaysAgo))
        assertEquals("10. Okt. 2022, 22:13", format(lastYear))
        assertEquals("Gestern, 20:13", format(now - TimeUnit.HOURS.toMillis(26)))
    }

    @Test
    @Config(qualifiers = "ja-rJP")
    fun `Japanese`() {
        assertEquals("11月4日 22:13", format(tenDaysAgo))
        assertEquals("2022年10月10日 22:13", format(lastYear))
        use24Hour(false)
        assertEquals("11月4日 午後10:13", format(tenDaysAgo))
    }

    @Test
    @Config(qualifiers = "fa-rIR")
    fun `Persian uses Persian digits`() {
        assertEquals("۴ نوامبر، ۲۲:۱۳", format(tenDaysAgo))
        assertEquals("۱۰ اکتبر ۲۰۲۲، ۲۲:۱۳", format(lastYear))
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun `Arabic uses Arabic digits and the 12-hour clock`() {
        assertEquals("٤ نوفمبر، ١٠:١٣ م", format(tenDaysAgo))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `uses the context's locale`() {
        val german = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.GERMANY) }
        )
        assertEquals("4. Nov., 22:13", format(tenDaysAgo, context = german))
    }
}
