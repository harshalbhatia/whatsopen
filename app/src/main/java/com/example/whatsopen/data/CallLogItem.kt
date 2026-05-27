package com.example.whatsopen.data

/**
 * A single entry from the device call log.
 *
 * @property id Stable row id from [android.provider.CallLog.Calls._ID]. Used as
 * a unique LazyColumn key so multiple rows with the same number/date don't collide.
 * @property callType One of [android.provider.CallLog.Calls.INCOMING_TYPE],
 * [android.provider.CallLog.Calls.OUTGOING_TYPE], or
 * [android.provider.CallLog.Calls.MISSED_TYPE].
 */
data class CallLogItem(
    val id: Long,
    val number: String,
    val date: Long,
    val callType: Int,
    val isContact: Boolean,
)
