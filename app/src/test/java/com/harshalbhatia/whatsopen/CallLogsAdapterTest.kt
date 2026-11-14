package com.harshalbhatia.whatsopen

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding
import com.harshalbhatia.whatsopen.databinding.ItemCallLogsHeaderBinding
import com.harshalbhatia.whatsopen.databinding.ItemCallLogsMessageBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallLogsAdapterTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_WhatsOpen)
    private val parent = FrameLayout(context)

    private val filters = mutableListOf<CallLogFilter>()
    private val messages = mutableListOf<CallLogsMessage>()
    private val clicks = mutableListOf<CallLogItem>()
    private val moreOptions = mutableListOf<Pair<CallLogItem, View>>()

    private val callbacks = object : CallLogsAdapter.Callbacks {
        override fun onFilterChanged(filter: CallLogFilter) { filters += filter }
        override fun onMessageAction(message: CallLogsMessage) { messages += message }
        override fun onCallClick(item: CallLogItem) { clicks += item }
        override fun onMoreOptions(item: CallLogItem, anchor: View) { moreOptions += item to anchor }
    }

    private val date = 1_700_000_000_000L
    private val time get() = DateFormatter.format(context, date)

    private fun call(kind: CallKind = CallKind.INCOMING, name: String? = null, count: Int = 1) =
        CallLogItem("+14155552671", "+1 415-555-2671", date, kind, name, count = count)

    /** The view for entry [position] of [entries], bound. */
    private fun bind(entries: List<CallLogsEntry>, position: Int = 0): View {
        val adapter = CallLogsAdapter(callbacks)
        adapter.submit(entries)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    private fun row(item: CallLogItem) = ItemCallLogBinding.bind(bind(listOf(CallLogsEntry.Row(item))))

    private fun header(filter: CallLogFilter?, count: Int?) =
        ItemCallLogsHeaderBinding.bind(bind(listOf(CallLogsEntry.Header(filter, count))))

    private fun message(message: CallLogsMessage) =
        ItemCallLogsMessageBinding.bind(bind(listOf(CallLogsEntry.Message(message))))

    // Rows

    @Test
    fun `first line is the name, or the number without one`() {
        assertEquals("Mom", row(call(name = "Mom")).primaryText.text.toString())
        assertEquals("+1 415-555-2671", row(call()).primaryText.text.toString())
    }

    @Test
    fun `second line is the call type and time`() {
        assertEquals("Outgoing · $time", row(call(CallKind.OUTGOING)).secondaryText.text.toString())
        assertEquals("Call · $time", row(call(CallKind.OTHER)).secondaryText.text.toString())
    }

    @Test
    fun `grouped calls show their count`() {
        assertEquals("Missed (3) · $time", row(call(CallKind.MISSED, count = 3)).secondaryText.text.toString())
    }

    @Test
    fun `row click passes the call`() {
        val item = call(name = "Mom")
        row(item).root.performClick()
        assertEquals(listOf(item), clicks)
    }

    @Test
    fun `more button passes the call and itself as the menu's anchor`() {
        val item = call()
        val binding = row(item)
        binding.moreButton.performClick()
        assertEquals(listOf(item to binding.moreButton), moreOptions)
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun `row reads one sentence, to or from the caller`() {
        assertEquals("Outgoing call to Mom, $time", row(call(CallKind.OUTGOING, "Mom")).root.contentDescription)
        assertEquals("Incoming call from Mom, $time", row(call(CallKind.INCOMING, "Mom")).root.contentDescription)
        assertEquals("Missed call from Mom, $time", row(call(CallKind.MISSED, "Mom")).root.contentDescription)
        assertEquals("Call with Mom, $time", row(call(CallKind.OTHER, "Mom")).root.contentDescription)
    }

    @Test
    fun `row reads the number without a name, and the count of grouped calls`() {
        assertEquals(
            "Missed call from +1 415-555-2671, $time, 3 calls",
            row(call(CallKind.MISSED, count = 3)).root.contentDescription
        )
    }

    @Test
    fun `row click action is labelled Open WhatsApp chat`() {
        val info = row(call()).root.createAccessibilityNodeInfo()
        val click = info.actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }
        assertEquals("Open WhatsApp chat", click.label)
    }

    @Test
    fun `row parts aren't read separately, the more button is`() {
        val binding = row(call(name = "Mom"))
        listOf(binding.primaryText, binding.secondaryText, binding.callTypeIcon, binding.divider).forEach {
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, it.importantForAccessibility)
        }
        assertTrue(binding.root.isFocusable)
        assertTrue(binding.moreButton.isFocusable)
        assertEquals("More options for Mom", binding.moreButton.contentDescription)
    }

    @Test
    fun `unknown call types get a neutral icon colour`() {
        assertNotNull(row(call(CallKind.OTHER)).callTypeIcon.imageTintList)
        assertNull(row(call(CallKind.MISSED)).callTypeIcon.imageTintList)
    }

    // Header

    @Test
    fun `header shows the chips as filtered and the call count`() {
        val binding = header(CallLogFilter(missed = true), 42)
        assertEquals(View.VISIBLE, binding.filterChipGroup.visibility)
        assertFalse(binding.chipIncoming.isChecked)
        assertTrue(binding.chipMissed.isChecked)
        assertFalse(binding.chipNonContactsOnly.isChecked)
        assertEquals("42 calls", binding.callCount.text.toString())
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.callCount.accessibilityLiveRegion)
        assertEquals("1 call", header(CallLogFilter(), 1).callCount.text.toString())
    }

    @Test
    fun `header without calls has no chips or count`() {
        val binding = header(null, null)
        assertEquals(View.GONE, binding.filterChipGroup.visibility)
        assertEquals(View.GONE, binding.callCount.visibility)
        assertEquals(context.getString(R.string.nav_call_logs), binding.title.text.toString())
    }

    @Test
    fun `turning a chip on reports the chips' new state, binding them doesn't`() {
        val binding = header(CallLogFilter(missed = true), 3)
        assertTrue(filters.isEmpty())

        assertTrue(binding.chipNonContactsOnly.isEnabled)
        binding.chipNonContactsOnly.performClick()
        assertEquals(listOf(CallLogFilter(missed = true, nonContactsOnly = true)), filters)
    }

    // Messages

    @Test
    fun `permission message explains and offers Allow access`() {
        val binding = message(CallLogsMessage.PERMISSION)
        assertEquals(context.getString(R.string.call_logs_permission_rationale), binding.messageText.text.toString())
        assertEquals("Allow access", binding.messageButton.text.toString())
        binding.messageButton.performClick()
        assertEquals(listOf(CallLogsMessage.PERMISSION), messages)
    }

    @Test
    fun `loading shows only the indicator`() {
        val binding = message(CallLogsMessage.LOADING)
        assertEquals(View.VISIBLE, binding.progress.visibility)
        assertEquals(View.GONE, binding.messageTitle.visibility)
        assertEquals(View.GONE, binding.messageButton.visibility)
        assertEquals(context.getString(R.string.call_logs_loading), binding.progress.contentDescription)
    }

    @Test
    fun `messages and their buttons`() {
        assertEquals("Open settings", message(CallLogsMessage.PERMISSION_BLOCKED).messageButton.text.toString())
        assertEquals("Try again", message(CallLogsMessage.ERROR).messageButton.text.toString())
        assertEquals("Clear filters", message(CallLogsMessage.NO_MATCHES).messageButton.text.toString())
        message(CallLogsMessage.NO_TELEPHONY).let {
            assertEquals("This device doesn't make phone calls", it.messageTitle.text.toString())
            assertEquals(View.GONE, it.messageButton.visibility)
        }
        assertEquals(View.GONE, message(CallLogsMessage.NO_CALLS).messageButton.visibility)
    }

    // Entries

    @Test
    fun `entries for each state`() {
        val noHeader = CallLogsEntry.Header(null, null)
        fun messageFor(state: CallLogsUiState, message: CallLogsMessage) =
            assertEquals(listOf(noHeader, CallLogsEntry.Message(message)), CallLogsEntry.forState(state))

        messageFor(CallLogsUiState.Loading, CallLogsMessage.LOADING)
        messageFor(CallLogsUiState.NoTelephony, CallLogsMessage.NO_TELEPHONY)
        messageFor(CallLogsUiState.NeedsPermission(false), CallLogsMessage.PERMISSION)
        messageFor(CallLogsUiState.NeedsPermission(true), CallLogsMessage.PERMISSION_BLOCKED)
        messageFor(CallLogsUiState.Error, CallLogsMessage.ERROR)
        messageFor(CallLogsUiState.Loaded(emptyList(), CallLogFilter(), 0), CallLogsMessage.NO_CALLS)

        val filter = CallLogFilter(missed = true)
        assertEquals(
            listOf(CallLogsEntry.Header(filter, 0), CallLogsEntry.Message(CallLogsMessage.NO_MATCHES)),
            CallLogsEntry.forState(CallLogsUiState.Loaded(emptyList(), filter, 5))
        )
        val items = listOf(call(CallKind.MISSED, count = 2), call(CallKind.MISSED, name = "Mom"))
        assertEquals(
            listOf(CallLogsEntry.Header(filter, 3)) + items.map { CallLogsEntry.Row(it) },
            CallLogsEntry.forState(CallLogsUiState.Loaded(items, filter, 5))
        )
    }

    @Test
    fun `a new count rebinds the header in place`() {
        val adapter = CallLogsAdapter(callbacks)
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        fun layOut() {
            list.measure(MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(4000, MeasureSpec.EXACTLY))
            list.layout(0, 0, 1080, 4000)
        }
        adapter.submit(listOf(CallLogsEntry.Header(CallLogFilter(), 2)) + List(2) { CallLogsEntry.Row(call(name = "Mom $it").copy(date = it.toLong())) })
        layOut()
        val headerView = list.getChildAt(0)

        adapter.submit(listOf(CallLogsEntry.Header(CallLogFilter(missed = true), 1), CallLogsEntry.Row(call(name = "Mom 1").copy(date = 1))))
        layOut()
        assertSame(headerView, list.getChildAt(0))
        assertEquals("1 call", ItemCallLogsHeaderBinding.bind(headerView).callCount.text.toString())
    }
}
