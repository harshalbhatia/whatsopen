package com.harshalbhatia.whatsopen

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding
import com.harshalbhatia.whatsopen.databinding.ItemCallLogsHeaderBinding
import com.harshalbhatia.whatsopen.databinding.ItemCallLogsMessageBinding

/** A message shown below the Call Logs header in place of calls. */
enum class CallLogsMessage { LOADING, NO_TELEPHONY, PERMISSION, PERMISSION_BLOCKED, ERROR, NO_CALLS, NO_MATCHES }

/** An item of the Call Logs list. */
sealed interface CallLogsEntry {

    /**
     * The title and subtitle, then the filter chips set to [filter] (none if null) and the
     * call count (none if null).
     */
    data class Header(val filter: CallLogFilter?, val callCount: Int?) : CallLogsEntry

    data class Message(val message: CallLogsMessage) : CallLogsEntry

    data class Row(val item: CallLogItem) : CallLogsEntry

    companion object {
        /** What the list shows in [state]. Filters only show once there are calls to filter. */
        fun forState(state: CallLogsUiState): List<CallLogsEntry> {
            val message = when (state) {
                is CallLogsUiState.Loaded -> when {
                    state.totalCalls == 0 -> CallLogsMessage.NO_CALLS
                    state.items.isEmpty() -> return listOf(Header(state.filter, 0), Message(CallLogsMessage.NO_MATCHES))
                    else -> return listOf(Header(state.filter, state.shownCalls)) + state.items.map(::Row)
                }
                CallLogsUiState.Loading -> CallLogsMessage.LOADING
                CallLogsUiState.NoTelephony -> CallLogsMessage.NO_TELEPHONY
                is CallLogsUiState.NeedsPermission ->
                    if (state.permanentlyDenied) CallLogsMessage.PERMISSION_BLOCKED else CallLogsMessage.PERMISSION
                CallLogsUiState.Error -> CallLogsMessage.ERROR
            }
            return listOf(Header(null, null), Message(message))
        }
    }
}

/**
 * The Call Logs list: a header with the title and filter chips, then the calls or a message.
 * It is one adapter so the header scrolls away with the calls.
 */
class CallLogsAdapter(private val callbacks: Callbacks) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** What the list's controls do. */
    interface Callbacks {
        /** A filter chip was turned on or off; [filter] is the chips' new state. */
        fun onFilterChanged(filter: CallLogFilter)

        /** The button of [message] was tapped. */
        fun onMessageAction(message: CallLogsMessage)

        /** A call's row was tapped. */
        fun onCallClick(item: CallLogItem)

        /** A call's ⋮ button, [anchor], was tapped. */
        fun onMoreOptions(item: CallLogItem, anchor: View)
    }

    private var entries: List<CallLogsEntry> = emptyList()

    /**
     * Shows [newEntries], animating what changed. Diffs on the calling thread: the list is a
     * few hundred items at most, and the result is then in place before this returns.
     */
    fun submit(newEntries: List<CallLogsEntry>) {
        val diff = DiffUtil.calculateDiff(Diff(entries, newEntries))
        entries = newEntries
        diff.dispatchUpdatesTo(this)
    }

    /** Rebinds every item, so times like "2 min. ago" catch up after a while in the background. */
    fun refreshTimes() = notifyItemRangeChanged(0, itemCount, REBIND)

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int = when (entries[position]) {
        is CallLogsEntry.Header -> TYPE_HEADER
        is CallLogsEntry.Message -> TYPE_MESSAGE
        is CallLogsEntry.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemCallLogsHeaderBinding.inflate(inflater, parent, false))
            TYPE_MESSAGE -> MessageHolder(ItemCallLogsMessageBinding.inflate(inflater, parent, false))
            else -> RowHolder(ItemCallLogBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is CallLogsEntry.Header -> (holder as HeaderHolder).bind(entry)
            is CallLogsEntry.Message -> (holder as MessageHolder).bind(entry.message)
            is CallLogsEntry.Row -> (holder as RowHolder).bind(entry.item)
        }
    }

    private inner class HeaderHolder(private val binding: ItemCallLogsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** True while bind() sets the chips, which mustn't count as the user changing them. */
        private var settingChips = false

        init {
            ViewCompat.setAccessibilityHeading(binding.title, true)
            listOf(binding.chipIncoming, binding.chipMissed, binding.chipNonContactsOnly).forEach { chip ->
                chip.setOnCheckedChangeListener { _, _ ->
                    if (!settingChips) {
                        callbacks.onFilterChanged(
                            CallLogFilter(
                                incoming = binding.chipIncoming.isChecked,
                                missed = binding.chipMissed.isChecked,
                                nonContactsOnly = binding.chipNonContactsOnly.isChecked,
                            )
                        )
                    }
                }
            }
        }

        fun bind(header: CallLogsEntry.Header) {
            binding.filterChipGroup.isVisible = header.filter != null
            header.filter?.let {
                settingChips = true
                binding.chipIncoming.isChecked = it.incoming
                binding.chipMissed.isChecked = it.missed
                binding.chipNonContactsOnly.isChecked = it.nonContactsOnly
                settingChips = false
            }
            val count = header.callCount?.let {
                itemView.resources.getQuantityString(R.plurals.call_logs_count, it, it)
            }
            binding.callCount.isVisible = count != null
            // The count is a live region: setting the same text again would announce it again.
            if (count != null && binding.callCount.text.toString() != count) binding.callCount.text = count
        }
    }

    private inner class MessageHolder(private val binding: ItemCallLogsMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var message: CallLogsMessage? = null

        init {
            binding.messageButton.setOnClickListener { message?.let(callbacks::onMessageAction) }
        }

        fun bind(message: CallLogsMessage) {
            this.message = message
            val loading = message == CallLogsMessage.LOADING
            binding.progress.isVisible = loading
            binding.icon.isVisible = !loading
            binding.messageTitle.isVisible = !loading
            binding.messageText.isVisible = !loading
            binding.messageButton.isVisible = false
            if (loading) return

            val (title, text, button) = when (message) {
                CallLogsMessage.NO_TELEPHONY ->
                    Triple(R.string.call_logs_no_telephony_title, R.string.call_logs_no_telephony_text, null)
                CallLogsMessage.PERMISSION ->
                    Triple(R.string.call_logs_permission_title, R.string.call_logs_permission_rationale, R.string.call_logs_allow_access)
                CallLogsMessage.PERMISSION_BLOCKED ->
                    Triple(R.string.call_logs_permission_blocked_title, R.string.call_logs_permission_blocked_text, R.string.open_settings)
                CallLogsMessage.ERROR ->
                    Triple(R.string.call_logs_error_title, R.string.call_logs_error_text, R.string.try_again)
                CallLogsMessage.NO_CALLS ->
                    Triple(R.string.empty_call_logs_title, R.string.empty_call_logs_description, null)
                else ->
                    Triple(R.string.empty_filtered_call_logs_title, R.string.empty_filtered_call_logs_description, R.string.clear_filters)
            }
            binding.messageTitle.setText(title)
            binding.messageText.setText(text)
            button?.let {
                binding.messageButton.setText(it)
                binding.messageButton.isVisible = true
            }
        }
    }

    private inner class RowHolder(private val binding: ItemCallLogBinding) : RecyclerView.ViewHolder(binding.root) {

        private var item: CallLogItem? = null

        init {
            binding.root.setOnClickListener { item?.let(callbacks::onCallClick) }
            binding.moreButton.setOnClickListener { anchor -> item?.let { callbacks.onMoreOptions(it, anchor) } }
            // Screen readers say "double-tap to open WhatsApp chat" rather than "to activate".
            ViewCompat.replaceAccessibilityAction(
                binding.root,
                AccessibilityActionCompat.ACTION_CLICK,
                itemView.context.getString(R.string.call_log_open_chat_action),
                null
            )
        }

        fun bind(item: CallLogItem) {
            this.item = item
            val context = itemView.context
            val who = item.name ?: item.displayNumber
            val time = DateFormatter.format(context, item.date)
            val type = context.getString(typeLabel(item.kind))

            binding.primaryText.text = who
            binding.secondaryText.text = context.getString(
                R.string.call_log_details,
                if (item.count > 1) context.getString(R.string.call_log_type_count, type, item.count) else type,
                time
            )
            binding.callTypeIcon.setImageResource(icon(item.kind))
            // An unknown type gets the incoming arrow, in a neutral colour since it may not be incoming.
            ImageViewCompat.setImageTintList(
                binding.callTypeIcon,
                if (item.kind == CallKind.OTHER) {
                    ColorStateList.valueOf(
                        MaterialColors.getColor(binding.callTypeIcon, com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                } else {
                    null
                }
            )

            val sentence = context.getString(description(item.kind), who, time)
            binding.root.contentDescription = if (item.count > 1) {
                context.resources.getQuantityString(R.plurals.call_log_description_count, item.count, sentence, item.count)
            } else {
                sentence
            }
            binding.moreButton.contentDescription = context.getString(R.string.call_log_more_options, who)
        }
    }

    /** Old and new entries. Every change rebinds in place, without the default crossfade. */
    private class Diff(private val old: List<CallLogsEntry>, private val new: List<CallLogsEntry>) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
            val before = old[oldPosition]
            val after = new[newPosition]
            return when (before) {
                is CallLogsEntry.Header -> after is CallLogsEntry.Header
                is CallLogsEntry.Message -> after is CallLogsEntry.Message
                is CallLogsEntry.Row ->
                    after is CallLogsEntry.Row && after.item.number == before.item.number && after.item.date == before.item.date
            }
        }

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == new[newPosition]

        // A payload makes RecyclerView reuse the item's view, so a chip being tapped isn't
        // swapped for a copy (losing its ripple and screen reader focus).
        override fun getChangePayload(oldPosition: Int, newPosition: Int): Any = REBIND
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_MESSAGE = 1
        const val TYPE_ROW = 2

        /** Payload for a change: everything is rebound, in the same view. */
        val REBIND = Any()

        @StringRes
        fun typeLabel(kind: CallKind): Int = when (kind) {
            CallKind.INCOMING -> R.string.call_log_type_incoming
            CallKind.OUTGOING -> R.string.call_log_type_outgoing
            CallKind.MISSED -> R.string.call_log_type_missed
            CallKind.OTHER -> R.string.call_log_type_other
        }

        @StringRes
        fun description(kind: CallKind): Int = when (kind) {
            CallKind.INCOMING -> R.string.call_log_description_incoming
            CallKind.OUTGOING -> R.string.call_log_description_outgoing
            CallKind.MISSED -> R.string.call_log_description_missed
            CallKind.OTHER -> R.string.call_log_description_other
        }

        @DrawableRes
        fun icon(kind: CallKind): Int = when (kind) {
            CallKind.INCOMING, CallKind.OTHER -> R.drawable.ic_call_incoming
            CallKind.OUTGOING -> R.drawable.ic_call_outgoing
            CallKind.MISSED -> R.drawable.ic_call_missed
        }
    }
}
