package com.example.whatsopen.data

class FakeCallLogRepository(
    var items: List<CallLogItem> = emptyList(),
    var shouldThrow: Boolean = false,
) : CallLogRepository {

    var lastIncludeContactStatus: Boolean? = null
        private set
    var loadCount: Int = 0
        private set

    override suspend fun loadCallLogs(includeContactStatus: Boolean): List<CallLogItem> {
        loadCount++
        lastIncludeContactStatus = includeContactStatus
        if (shouldThrow) throw RuntimeException("boom")
        return items
    }
}
