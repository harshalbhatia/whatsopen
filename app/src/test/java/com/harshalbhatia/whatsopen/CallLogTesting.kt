package com.harshalbhatia.whatsopen

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.CallLog

/** A call log row, in [CallLogReader.PROJECTION]'s column order. */
fun callRow(
    number: String?,
    date: Long = 0L,
    type: Int = CallLog.Calls.INCOMING_TYPE,
    presentation: Int = CallLog.Calls.PRESENTATION_ALLOWED,
    countryIso: String? = null,
    name: String? = null,
    lookupUri: String? = null,
    normalized: String? = null,
): Array<Any?> = arrayOf(number, date, type, presentation, countryIso, name, lookupUri, normalized)

/** A cursor over [rows], with the columns the call log reader asks for. */
fun callCursor(rows: List<Array<Any?>>): MatrixCursor =
    MatrixCursor(CallLogReader.PROJECTION).apply { rows.forEach(::addRow) }

/**
 * Stands in for the call log provider: returns [rows], no cursor if they are null, or throws
 * [error]. Remembers the last query's URI and arguments. Register it with
 * `Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)`.
 */
class FakeCallLogProvider : ContentProvider() {
    var rows: List<Array<Any?>>? = emptyList()
    var error: RuntimeException? = null
    var queries = 0
        private set
    var lastUri: Uri? = null
        private set
    var lastArgs: Bundle? = null
        private set

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor? {
        queries++
        lastUri = uri
        lastArgs = queryArgs
        error?.let { throw it }
        return rows?.let(::callCursor)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = query(uri, projection, null as Bundle?, null)

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
