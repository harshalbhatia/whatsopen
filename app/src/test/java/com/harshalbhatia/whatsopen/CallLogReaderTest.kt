package com.harshalbhatia.whatsopen

import android.app.Application
import android.content.ContentResolver
import android.provider.CallLog
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallLogReaderTest {

    private fun read(vararg rows: Array<Any?>, region: String? = "US", limit: Int = CallLogReader.LIMIT) =
        CallLogReader.read(callCursor(rows.toList()), region, limit)

    private fun call(number: String, kind: CallKind, date: Long = 0L, name: String? = null, lookupUri: String? = null) =
        CallLogItem(number, number, date, kind, name, lookupUri)

    /** [text] without the invisible bidi marks that keep it left to right. */
    private fun visible(text: String) = text.filterNot { it in "\u200E\u200F\u202A\u202B\u202C\u202D\u202E" }

    // Mapping

    @Test
    fun `maps a row to a call`() {
        val call = read(callRow("(415) 555-2671", date = 1_700_000_000_000L, type = CallLog.Calls.OUTGOING_TYPE)).single()
        assertEquals("+14155552671", call.number)
        assertEquals(1_700_000_000_000L, call.date)
        assertEquals(CallKind.OUTGOING, call.kind)
        assertNull(call.name)
        assertEquals(1, call.count)
        assertFalse(call.isContact)
    }

    @Test
    fun `display number is formatted`() {
        val call = read(callRow("+447911123456")).single()
        assertEquals("+44 7911 123456", visible(call.displayNumber))
    }

    @Test
    fun `cached name is the name and makes it a contact`() {
        val call = read(callRow("+447911123456", name = "Mom")).single()
        assertEquals("Mom", call.name)
        assertTrue(call.isContact)
    }

    @Test
    fun `cached lookup URI alone makes it a contact`() {
        val call = read(callRow("+447911123456", lookupUri = "content://com.android.contacts/contacts/lookup/abc/1")).single()
        assertNull(call.name)
        assertTrue(call.isContact)
    }

    @Test
    fun `blank cached name and lookup URI don't make it a contact`() {
        val call = read(callRow("+447911123456", name = " ", lookupUri = "")).single()
        assertNull(call.name)
        assertNull(call.lookupUri)
        assertFalse(call.isContact)
    }

    @Test
    fun `skips withheld, unknown and payphone numbers`() {
        val calls = read(
            callRow("+447911123456", presentation = CallLog.Calls.PRESENTATION_RESTRICTED),
            callRow("+447911123457", presentation = CallLog.Calls.PRESENTATION_UNKNOWN),
            callRow("+447911123458", presentation = CallLog.Calls.PRESENTATION_PAYPHONE),
            callRow("+447911123459"),
        )
        assertEquals(listOf("+447911123459"), calls.map { it.number })
    }

    @Test
    fun `skips blank and missing numbers`() {
        val calls = read(callRow(null), callRow(""), callRow("   "), callRow("+447911123459"))
        assertEquals(listOf("+447911123459"), calls.map { it.number })
    }

    @Test
    fun `skips numbers WhatsApp can't open`() {
        assertEquals(emptyList<CallLogItem>(), read(callRow("123"), callRow("*86")))
    }

    @Test
    fun `reads a number in the country its call was made in`() {
        // A UK number called while in the UK, read on a phone now in the US.
        val call = read(callRow("07911 123456", countryIso = "GB"), region = "US").single()
        assertEquals("+447911123456", call.number)
    }

    @Test
    fun `accepts a lowercase call country`() {
        assertEquals("+447911123456", read(callRow("07911 123456", countryIso = "gb"), region = "US").single().number)
    }

    @Test
    fun `falls back to the default region without a call country`() {
        assertEquals("+447911123456", read(callRow("07911 123456", countryIso = ""), region = "GB").single().number)
    }

    @Test
    fun `prefers the cached normalized number`() {
        // Read in the US, the national number alone wouldn't be valid.
        val call = read(callRow("07911 123456", normalized = "+447911123456"), region = "US").single()
        assertEquals("+447911123456", call.number)
    }

    @Test
    fun `ignores a cached normalized number that isn't E164`() {
        val call = read(callRow("(415) 555-2671", normalized = "4155552671"), region = "US").single()
        assertEquals("+14155552671", call.number)
    }

    @Test
    fun `reads at most 200 rows`() {
        val rows = (0 until 250).map { callRow("+4479111%05d".format(it), date = 250L - it) }
        val calls = CallLogReader.read(callCursor(rows), "US")
        assertEquals(CallLogReader.LIMIT, calls.size)
        assertEquals(200, calls.size)
        assertEquals("+447911100199", calls.last().number)
    }

    @Test
    fun `skipped rows count towards the limit`() {
        val calls = read(
            callRow("+447911123450", presentation = CallLog.Calls.PRESENTATION_RESTRICTED),
            callRow("+447911123451"),
            callRow("+447911123452"),
            callRow("+447911123453"),
            limit = 3,
        )
        assertEquals(listOf("+447911123451", "+447911123452"), calls.map { it.number })
    }

    @Test
    fun `rejected and blocked calls read as missed`() {
        val calls = read(
            callRow("+447911123450", type = CallLog.Calls.REJECTED_TYPE),
            callRow("+447911123451", type = CallLog.Calls.BLOCKED_TYPE),
            callRow("+447911123452", type = CallLog.Calls.MISSED_TYPE),
        )
        assertEquals(List(3) { CallKind.MISSED }, calls.map { it.kind })
    }

    // Kinds

    @Test
    fun `kinds of call types`() {
        assertEquals(CallKind.INCOMING, CallLogReader.kindOf(CallLog.Calls.INCOMING_TYPE))
        assertEquals(CallKind.INCOMING, CallLogReader.kindOf(CallLog.Calls.VOICEMAIL_TYPE))
        assertEquals(CallKind.INCOMING, CallLogReader.kindOf(CallLog.Calls.ANSWERED_EXTERNALLY_TYPE))
        assertEquals(CallKind.OUTGOING, CallLogReader.kindOf(CallLog.Calls.OUTGOING_TYPE))
        assertEquals(CallKind.MISSED, CallLogReader.kindOf(CallLog.Calls.MISSED_TYPE))
        assertEquals(CallKind.MISSED, CallLogReader.kindOf(CallLog.Calls.REJECTED_TYPE))
        assertEquals(CallKind.MISSED, CallLogReader.kindOf(CallLog.Calls.BLOCKED_TYPE))
        assertEquals(CallKind.OTHER, CallLogReader.kindOf(0))
        assertEquals(CallKind.OTHER, CallLogReader.kindOf(1000))
    }

    // Grouping

    @Test
    fun `groups consecutive calls with the same number and kind, keeping the latest`() {
        val rows = CallLogReader.group(
            listOf(
                call("+1", CallKind.MISSED, date = 30),
                call("+1", CallKind.MISSED, date = 20),
                call("+1", CallKind.MISSED, date = 10),
                call("+2", CallKind.INCOMING, date = 5),
            )
        )
        assertEquals(listOf("+1" to 3, "+2" to 1), rows.map { it.number to it.count })
        assertEquals(30L, rows.first().date)
    }

    @Test
    fun `doesn't group calls of different kinds or apart`() {
        val rows = CallLogReader.group(
            listOf(
                call("+1", CallKind.MISSED, date = 40),
                call("+1", CallKind.OUTGOING, date = 30),
                call("+2", CallKind.INCOMING, date = 20),
                call("+1", CallKind.OUTGOING, date = 10),
            )
        )
        assertEquals(listOf(1, 1, 1, 1), rows.map { it.count })
    }

    // Filters

    private val calls = listOf(
        call("+1", CallKind.INCOMING),
        call("+2", CallKind.OUTGOING),
        call("+3", CallKind.MISSED),
        call("+4", CallKind.OTHER),
        call("+5", CallKind.INCOMING, name = "Mom"),
        call("+6", CallKind.MISSED, lookupUri = "content://com.android.contacts/contacts/lookup/abc/1"),
    )

    private fun numbersMatching(filter: CallLogFilter) = calls.filter(filter::matches).map { it.number }

    @Test
    fun `no filter matches every call`() {
        assertEquals(calls.map { it.number }, numbersMatching(CallLogFilter()))
    }

    @Test
    fun `incoming and missed filters`() {
        assertEquals(listOf("+1", "+5"), numbersMatching(CallLogFilter(incoming = true)))
        assertEquals(listOf("+3", "+6"), numbersMatching(CallLogFilter(missed = true)))
        assertEquals(listOf("+1", "+3", "+5", "+6"), numbersMatching(CallLogFilter(incoming = true, missed = true)))
    }

    @Test
    fun `non-contacts filter leaves out calls with a cached name or lookup URI`() {
        assertEquals(listOf("+1", "+2", "+3", "+4"), numbersMatching(CallLogFilter(nonContactsOnly = true)))
        assertEquals(listOf("+3"), numbersMatching(CallLogFilter(missed = true, nonContactsOnly = true)))
    }

    // Query

    private val resolver: ContentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @Test
    fun `query asks for 200 newest first, without LIMIT in the sort order`() {
        val provider = Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
        provider.rows = listOf(callRow("+447911123456"))

        assertEquals(listOf("+447911123456"), CallLogReader.query(resolver, "US")?.map { it.number })
        assertEquals("200", provider.lastUri?.getQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY))
        assertEquals(200, provider.lastArgs?.getInt(ContentResolver.QUERY_ARG_LIMIT))
        assertEquals("date DESC", provider.lastArgs?.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER))
    }

    @Test
    fun `query returns null without a cursor`() {
        val provider = Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
        provider.rows = null
        assertNull(CallLogReader.query(resolver, "US"))
    }

    @Test(expected = SecurityException::class)
    fun `query lets a SecurityException through`() {
        val provider = Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
        provider.error = SecurityException("Permission denial")
        CallLogReader.query(resolver, "US")
    }
}
