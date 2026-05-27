package com.harshalbhatia.whatsopen

import android.provider.CallLog
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallLogsAdapterTest {

    private fun makeAdapter(onClick: (String) -> Unit = {}): CallLogsAdapter {
        return CallLogsAdapter(onClick)
    }

    private fun bindItem(item: CallLogItem, onClick: (String) -> Unit = {}): CallLogsAdapter.ViewHolder {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_WhatsOpen)
        val parent = FrameLayout(activity)
        val adapter = makeAdapter(onClick)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.submitList(listOf(item))
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    @Test
    fun `binds country code and phone number`() {
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.OUTGOING_TYPE, false, null)
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        assertEquals("+1", binding.countryCode.text.toString())
        assertEquals("4155551234", binding.phoneNumber.text.toString())
        assertTrue(binding.countryCode.isShown || binding.countryCode.visibility == 0)
    }

    @Test
    fun `hides country code when split returns empty`() {
        val holder = bindItem(
            CallLogItem("1234567", 1_700_000_000_000L, CallLog.Calls.OUTGOING_TYPE, false, null)
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        assertEquals(android.view.View.GONE, binding.countryCode.visibility)
    }

    @Test
    fun `shows contact name when present`() {
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.OUTGOING_TYPE, true, "Mom")
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        assertEquals("Mom", binding.contactName.text.toString())
        assertEquals(android.view.View.VISIBLE, binding.contactName.visibility)
    }

    @Test
    fun `hides contact name when null`() {
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.OUTGOING_TYPE, false, null)
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        assertEquals(android.view.View.GONE, binding.contactName.visibility)
    }

    @Test
    fun `card click invokes callback with raw number`() {
        var captured: String? = null
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.OUTGOING_TYPE, false, null)
        ) { captured = it }
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        binding.callLogCard.performClick()
        assertEquals("+14155551234", captured)
    }

    @Test
    fun `icon button click invokes callback with raw number`() {
        var captured: String? = null
        val holder = bindItem(
            CallLogItem("+919876543210", 1_700_000_000_000L, CallLog.Calls.INCOMING_TYPE, false, null)
        ) { captured = it }
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        binding.openChatButton.performClick()
        assertEquals("+919876543210", captured)
    }

    @Test
    fun `incoming call uses incoming icon`() {
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.INCOMING_TYPE, false, null)
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        // Icon resource set; verify content description reflects type
        assertTrue(
            binding.callLogCard.contentDescription.toString().startsWith("Incoming call")
        )
    }

    @Test
    fun `missed call content description includes Missed`() {
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.MISSED_TYPE, false, null)
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        assertTrue(
            binding.callLogCard.contentDescription.toString().startsWith("Missed call")
        )
    }

    @Test
    fun `content description uses contact name when present`() {
        val holder = bindItem(
            CallLogItem("+14155551234", 1_700_000_000_000L, CallLog.Calls.OUTGOING_TYPE, true, "Mom")
        )
        val binding = com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding.bind(holder.itemView)
        assertTrue(
            "expected Mom in $binding.callLogCard.contentDescription",
            binding.callLogCard.contentDescription.toString().contains("Mom")
        )
    }
}
