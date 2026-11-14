package com.harshalbhatia.whatsopen

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.annotation.VisibleForTesting

/** How a call went, as the Call Logs tab shows it. */
enum class CallKind {
    INCOMING,
    OUTGOING,

    /** Missed, rejected or blocked: the caller didn't get through. */
    MISSED,

    /** A call type this app doesn't know, e.g. one an OEM added. Shown without saying which way it went. */
    OTHER,
}

/**
 * A row of the Call Logs tab: one call, or several calls in a row with the same number and kind.
 *
 * @property number The number in E.164 ("+447911123456"), as WhatsApp needs it.
 * @property displayNumber [number] formatted for reading, kept left to right in right-to-left text.
 * @property date When the latest of the calls was, in milliseconds since the epoch.
 * @property name The contact name the call log has cached for the number, if any.
 * @property lookupUri The call log's cached lookup URI for the number's contact, if any.
 * @property count How many calls the row stands for.
 */
data class CallLogItem(
    val number: String,
    val displayNumber: String,
    val date: Long,
    val kind: CallKind,
    val name: String? = null,
    val lookupUri: String? = null,
    val count: Int = 1,
) {
    /** Whether the number belongs to a contact, going by what the call log cached. */
    val isContact: Boolean get() = name != null || lookupUri != null
}

/**
 * The Call Logs tab's filter chips. With both [incoming] and [missed] on, calls of either kind
 * show; with neither, calls of every kind do.
 */
data class CallLogFilter(
    val incoming: Boolean = false,
    val missed: Boolean = false,
    val nonContactsOnly: Boolean = false,
) {
    /** Whether [call] passes every chip that is on. */
    fun matches(call: CallLogItem): Boolean {
        val kindMatches = when {
            incoming || missed -> (incoming && call.kind == CallKind.INCOMING) || (missed && call.kind == CallKind.MISSED)
            else -> true
        }
        return kindMatches && !(nonContactsOnly && call.isContact)
    }
}

/**
 * Reads recent calls from the call log. Contact names come from the call log's own cached
 * columns, so only READ_CALL_LOG is needed. Nothing read is kept or logged.
 */
object CallLogReader {

    /** The most rows read from the call log. */
    const val LIMIT = 200

    private const val SORT_ORDER = "${CallLog.Calls.DATE} DESC"

    /** What the call log's cached normalized number looks like when it is usable as is. */
    private val E164 = Regex("""\+[1-9]\d{1,14}""")

    @VisibleForTesting
    internal val PROJECTION = arrayOf(
        CallLog.Calls.NUMBER,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE,
        CallLog.Calls.NUMBER_PRESENTATION,
        CallLog.Calls.COUNTRY_ISO,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.CACHED_LOOKUP_URI,
        CallLog.Calls.CACHED_NORMALIZED_NUMBER,
    )

    /**
     * The latest calls, newest first, from at most [LIMIT] rows of the call log. Blocks; call
     * it off the main thread.
     *
     * @param defaultRegion The region for numbers whose call has no country recorded, and for
     *   formatting. An ISO 3166 code, or null if unknown.
     * @return The calls, or null if the call log couldn't be queried.
     * @throws SecurityException Without READ_CALL_LOG.
     */
    fun query(resolver: ContentResolver, defaultRegion: String?): List<CallLogItem>? {
        // The call log provider has long honoured this parameter; the arguments below are the
        // documented way on Android 11+. Neither is relied on: read() stops at LIMIT anyway.
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, LIMIT.toString())
            .build()
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, SORT_ORDER)
                putInt(ContentResolver.QUERY_ARG_LIMIT, LIMIT)
            }
            resolver.query(uri, PROJECTION, args, null)
        } else {
            resolver.query(uri, PROJECTION, null, null, SORT_ORDER)
        }
        return cursor?.use { read(it, defaultRegion) }
    }

    /**
     * Maps up to [limit] rows of [cursor] to calls, in cursor order. Skips rows with a withheld,
     * unknown or payphone number, a blank number, or one that can't be made E.164 (short
     * codes and the like, which WhatsApp can't open).
     *
     * Each number is read in the region its call was made in (the row's country), falling
     * back to [defaultRegion].
     */
    @VisibleForTesting
    internal fun read(cursor: Cursor, defaultRegion: String?, limit: Int = LIMIT): List<CallLogItem> {
        val numberColumn = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val dateColumn = cursor.getColumnIndex(CallLog.Calls.DATE)
        val typeColumn = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val presentationColumn = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
        val countryColumn = cursor.getColumnIndex(CallLog.Calls.COUNTRY_ISO)
        val nameColumn = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val lookupColumn = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
        val normalizedColumn = cursor.getColumnIndex(CallLog.Calls.CACHED_NORMALIZED_NUMBER)

        val bidi = BidiFormatter.getInstance()
        val calls = ArrayList<CallLogItem>()
        var rows = 0
        while (rows < limit && cursor.moveToNext()) {
            rows++
            val presentation = cursor.intOrNull(presentationColumn) ?: CallLog.Calls.PRESENTATION_ALLOWED
            if (presentation != CallLog.Calls.PRESENTATION_ALLOWED) continue
            val raw = cursor.stringOrNull(numberColumn)?.takeIf { it.isNotBlank() } ?: continue

            val region = DefaultRegion.cleanIso(cursor.stringOrNull(countryColumn)) ?: defaultRegion
            val e164 = cursor.stringOrNull(normalizedColumn)?.takeIf { E164.matches(it) }
                ?: PhoneNumbers.normalize(raw, region)
                ?: continue
            val formatted = PhoneNumberUtils.formatNumber(e164, defaultRegion ?: "") ?: e164

            calls += CallLogItem(
                number = e164,
                displayNumber = bidi.unicodeWrap(formatted, TextDirectionHeuristics.LTR),
                date = cursor.longOrNull(dateColumn) ?: 0L,
                kind = kindOf(cursor.intOrNull(typeColumn) ?: 0),
                name = cursor.stringOrNull(nameColumn)?.takeIf { it.isNotBlank() },
                lookupUri = cursor.stringOrNull(lookupColumn)?.takeIf { it.isNotBlank() },
            )
        }
        return calls
    }

    /**
     * Merges each run of consecutive calls with the same number and kind into one row whose
     * [CallLogItem.count] says how many it holds. The row keeps the latest call's details.
     * Calls of different kinds stay apart, so "Missed (3)" always means three missed calls.
     */
    fun group(calls: List<CallLogItem>): List<CallLogItem> {
        val rows = ArrayList<CallLogItem>(calls.size)
        for (call in calls) {
            val last = rows.lastOrNull()
            if (last != null && last.number == call.number && last.kind == call.kind) {
                rows[rows.lastIndex] = last.copy(count = last.count + call.count)
            } else {
                rows += call
            }
        }
        return rows
    }

    /**
     * The kind of a call log TYPE. Rejected and blocked calls count as missed; voicemail and
     * calls answered on another device as incoming.
     */
    // ANSWERED_EXTERNALLY_TYPE is a constant inlined at compile time; older Androids never return it.
    @SuppressLint("InlinedApi")
    fun kindOf(type: Int): CallKind = when (type) {
        CallLog.Calls.INCOMING_TYPE,
        CallLog.Calls.VOICEMAIL_TYPE,
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallKind.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE,
        CallLog.Calls.REJECTED_TYPE,
        CallLog.Calls.BLOCKED_TYPE -> CallKind.MISSED
        else -> CallKind.OTHER
    }

    private fun Cursor.stringOrNull(column: Int): String? =
        if (column < 0 || isNull(column)) null else getString(column)

    private fun Cursor.intOrNull(column: Int): Int? =
        if (column < 0 || isNull(column)) null else getInt(column)

    private fun Cursor.longOrNull(column: Int): Long? =
        if (column < 0 || isNull(column)) null else getLong(column)
}
