package com.example.whatsopen.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CallLogRepository {
    /**
     * Loads call log entries ordered by date DESC.
     *
     * @param includeContactStatus when true, also queries [ContactsContract] to
     * determine whether each number maps to a saved contact. Caller is
     * responsible for ensuring the READ_CONTACTS permission is granted.
     */
    suspend fun loadCallLogs(includeContactStatus: Boolean): List<CallLogItem>
}

class DefaultCallLogRepository(
    private val contentResolver: ContentResolver,
) : CallLogRepository {

    override suspend fun loadCallLogs(includeContactStatus: Boolean): List<CallLogItem> =
        withContext(Dispatchers.IO) {
            val raw = queryCallLog()
            val contactNumbers = if (includeContactStatus) {
                lookupContactNumbers(raw.map { it.number }.toSet())
            } else {
                emptySet()
            }
            raw.map { entry ->
                CallLogItem(
                    number = entry.number,
                    date = entry.date,
                    callType = entry.type,
                    isContact = entry.number in contactNumbers,
                )
            }
        }

    private data class RawEntry(val number: String, val date: Long, val type: Int)

    private fun queryCallLog(): List<RawEntry> {
        val results = mutableListOf<RawEntry>()
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    RawEntry(
                        number = cursor.getString(0) ?: "",
                        date = cursor.getLong(1),
                        type = cursor.getInt(2),
                    ),
                )
            }
        }
        return results
    }

    private fun lookupContactNumbers(numbers: Set<String>): Set<String> {
        val contactNumbers = mutableSetOf<String>()
        for (number in numbers) {
            if (number.isBlank()) continue
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            try {
                contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contactNumbers.add(number)
                    }
                }
            } catch (_: Exception) {
                // SecurityException (permission revoked) or other I/O errors; skip silently.
            }
        }
        return contactNumbers
    }
}
