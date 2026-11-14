package com.harshalbhatia.whatsopen

import android.content.Context
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.core.os.ConfigurationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Formats call times for the Call Logs tab in the user's language and 12/24-hour setting. */
object DateFormatter {

    private val relativeWindow = TimeUnit.DAYS.toMillis(7)

    /**
     * [timestamp] relative to [now] within 7 days of it ("Now, 3:45 PM", "5 min. ago, 3:45 PM",
     * "Yesterday, 3:45 PM"), otherwise as a date and time in the locale's order
     * ("Mar 5, 3:45 PM"), with the year only when it isn't [now]'s year.
     */
    fun format(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        // Not the skeleton's "j", the locale's usual clock: it ignores the user's setting.
        val time = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val sameYear = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)

        if (now - timestamp < relativeWindow) {
            // As DateUtils.getRelativeDateTimeString() does it, which can't be used as is: it
            // formats the time in the locale's usual clock whatever the user's setting.
            val sameDay = sameYear && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            val formatter = RelativeDateTimeFormatter.getInstance(
                ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.LONG,
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
            )
            val day = if (abs(now - timestamp) < DateUtils.MINUTE_IN_MILLIS) {
                // DateUtils says "0 min. ago".
                formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
            } else {
                DateUtils.getRelativeTimeSpanString(
                    timestamp,
                    now,
                    if (sameDay) DateUtils.MINUTE_IN_MILLIS else DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            }
            return formatter.combineDateAndTime(day, format(locale, time, timestamp))
        }
        return format(locale, (if (sameYear) "MMMd" else "yMMMd") + time, timestamp)
    }

    private fun format(locale: Locale, skeleton: String, millis: Long): String =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(millis))
}
