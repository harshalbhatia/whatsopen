package com.example.whatsopen.data

/**
 * A single entry from the device call log.
 *
 * @property callType One of [android.provider.CallLog.Calls.INCOMING_TYPE],
 * [android.provider.CallLog.Calls.OUTGOING_TYPE], or
 * [android.provider.CallLog.Calls.MISSED_TYPE].
 */
data class CallLogItem(
    val number: String,
    val date: Long,
    val callType: Int,
    val isContact: Boolean,
)
