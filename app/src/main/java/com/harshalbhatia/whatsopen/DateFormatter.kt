package com.harshalbhatia.whatsopen

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateFormatter {

    private val absoluteFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    private val relativeWindow = TimeUnit.DAYS.toMillis(7)

    fun format(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String {
        return if (now - timestamp < relativeWindow) {
            DateUtils.getRelativeDateTimeString(
                context,
                timestamp,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE or DateUtils.FORMAT_ABBREV_TIME
            ).toString()
        } else {
            absoluteFormat.format(Date(timestamp))
        }
    }
}
