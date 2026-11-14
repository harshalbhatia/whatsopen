package com.harshalbhatia.whatsopen

import android.app.Application
import android.content.Context
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class CallLogsViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: FakeCallLogProvider
    private lateinit var viewModel: CallLogsViewModel

    @Before
    fun setUp() {
        // Reads the call log right away, on the test's thread.
        CallLogsViewModel.ioDispatcher = Dispatchers.Unconfined
        shadowOf(app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).setNetworkCountryIso("us")
        provider = Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
        viewModel = CallLogsViewModel(app)
    }

    @After
    fun tearDown() {
        CallLogsViewModel.ioDispatcher = Dispatchers.IO
    }

    private val loaded get() = viewModel.state.value as CallLogsUiState.Loaded

    @Test
    fun `starts loading`() {
        assertEquals(CallLogsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `load groups the calls read`() {
        provider.rows = listOf(
            callRow("(415) 555-2671", date = 30, type = CallLog.Calls.MISSED_TYPE),
            callRow("(415) 555-2671", date = 20, type = CallLog.Calls.MISSED_TYPE),
            callRow("+447911123456", date = 10, name = "Mom"),
        )
        viewModel.load()

        assertEquals(listOf("+14155552671" to 2, "+447911123456" to 1), loaded.items.map { it.number to it.count })
        assertEquals(3, loaded.totalCalls)
        assertEquals(3, loaded.shownCalls)
        assertEquals(CallLogFilter(), loaded.filter)
    }

    @Test
    fun `an empty call log loads as no calls`() {
        viewModel.load()
        assertEquals(CallLogsUiState.Loaded(emptyList(), CallLogFilter(), 0), viewModel.state.value)
    }

    @Test
    fun `a SecurityException asks for the permission in settings instead of crashing`() {
        provider.error = SecurityException("Permission denial")
        viewModel.load()
        assertEquals(CallLogsUiState.NeedsPermission(permanentlyDenied = true), viewModel.state.value)
    }

    @Test
    fun `other exceptions are an error`() {
        provider.error = IllegalArgumentException("Invalid column")
        viewModel.load()
        assertEquals(CallLogsUiState.Error, viewModel.state.value)
    }

    @Test
    fun `no cursor is an error`() {
        provider.rows = null
        viewModel.load()
        assertEquals(CallLogsUiState.Error, viewModel.state.value)
    }

    @Test
    fun `filters before grouping, without reading the call log again`() {
        provider.rows = listOf(
            callRow("+447911123456", date = 30, type = CallLog.Calls.MISSED_TYPE),
            callRow("+447911123456", date = 20, type = CallLog.Calls.INCOMING_TYPE),
            callRow("+447911123456", date = 10, type = CallLog.Calls.REJECTED_TYPE),
            callRow("+447911123457", date = 5, type = CallLog.Calls.OUTGOING_TYPE),
        )
        viewModel.load()
        assertEquals(4, loaded.items.size)

        viewModel.setFilter(CallLogFilter(missed = true))
        assertEquals(listOf("+447911123456" to 2), loaded.items.map { it.number to it.count })
        assertEquals(CallLogFilter(missed = true), loaded.filter)
        assertEquals(4, loaded.totalCalls)
        assertEquals(1, provider.queries)
    }

    @Test
    fun `the filter carries over to the next load`() {
        provider.rows = listOf(callRow("+447911123456", name = "Mom"), callRow("+447911123457"))
        viewModel.setFilter(CallLogFilter(nonContactsOnly = true))
        viewModel.load()
        assertEquals(listOf("+447911123457"), loaded.items.map { it.number })
    }

    @Test
    fun `losing the permission drops the calls`() {
        provider.rows = listOf(callRow("+447911123456"))
        viewModel.load()
        viewModel.showNeedsPermission(permanentlyDenied = false)
        viewModel.setFilter(CallLogFilter(incoming = true))
        assertEquals(CallLogsUiState.NeedsPermission(permanentlyDenied = false), viewModel.state.value)
    }

    @Test
    fun `no telephony`() {
        viewModel.showNoTelephony()
        assertEquals(CallLogsUiState.NoTelephony, viewModel.state.value)
    }

    @Test
    fun `a reload keeps the calls showing until the new ones arrive`() {
        val pending = ArrayDeque<Runnable>()
        CallLogsViewModel.ioDispatcher = Executor { pending.addLast(it) }.asCoroutineDispatcher()
        provider.rows = listOf(callRow("+447911123456"))

        viewModel.load()
        assertEquals(CallLogsUiState.Loading, viewModel.state.value)
        pending.removeFirst().run()
        assertEquals(1, loaded.items.size)

        provider.rows = listOf(callRow("+447911123456"), callRow("+447911123457"))
        viewModel.load()
        assertEquals(1, loaded.items.size)
        pending.removeFirst().run()
        assertEquals(2, loaded.items.size)
        assertTrue(pending.isEmpty())
    }
}
