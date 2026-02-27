package com.example.whatsopen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsopen.databinding.FragmentCallLogsBinding
import com.example.whatsopen.databinding.ItemCallLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CallLogsFragment : Fragment() {
    private var _binding: FragmentCallLogsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CallLogsAdapter

    private var allCallLogs: List<CallLogItem> = emptyList()
    private var selectedCallType: Int? = null
    private var selectedContactFilter: ContactFilter = ContactFilter.ALL

    private enum class ContactFilter { ALL, CONTACTS_ONLY, NON_CONTACTS_ONLY }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadCallLogs()
        } else {
            showPermissionDeniedState()
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableContactFilterChips()
            loadCallLogs()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CallLogsAdapter { number ->
            WhatsAppLauncher.openChat(requireContext(), number)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CallLogsFragment.adapter
        }

        binding.callTypeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCallType = when {
                checkedIds.contains(R.id.chip_incoming) -> CallLog.Calls.INCOMING_TYPE
                checkedIds.contains(R.id.chip_outgoing) -> CallLog.Calls.OUTGOING_TYPE
                checkedIds.contains(R.id.chip_missed) -> CallLog.Calls.MISSED_TYPE
                else -> null
            }
            applyFilters()
        }

        binding.contactStatusChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedContactFilter = when {
                checkedIds.contains(R.id.chip_contacts_only) -> ContactFilter.CONTACTS_ONLY
                checkedIds.contains(R.id.chip_non_contacts_only) -> ContactFilter.NON_CONTACTS_ONLY
                else -> ContactFilter.ALL
            }
            applyFilters()
        }

        updateContactChipsState()

        binding.chipContactsOnly.setOnClickListener {
            if (!hasContactsPermission()) {
                requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        binding.chipNonContactsOnly.setOnClickListener {
            if (!hasContactsPermission()) {
                requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        binding.grantPermissionButton.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                requestPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
        }

        checkPermissionAndLoadLogs()
    }

    private fun checkPermissionAndLoadLogs() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadCallLogs()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG) -> {
                showPermissionDeniedState()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateContactChipsState() {
        val hasPermission = hasContactsPermission()
        binding.chipContactsOnly.isEnabled = hasPermission
        binding.chipNonContactsOnly.isEnabled = hasPermission
        if (!hasPermission) {
            binding.chipAllContacts.isChecked = true
            selectedContactFilter = ContactFilter.ALL
        }
    }

    private fun enableContactFilterChips() {
        binding.chipContactsOnly.isEnabled = true
        binding.chipNonContactsOnly.isEnabled = true
    }

    private fun showPermissionDeniedState() {
        binding.recyclerView.isVisible = false
        binding.callTypeFilterScroll.isVisible = false
        binding.contactFilterScroll.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyStateTitle.text = getString(R.string.empty_call_logs_permission_title)
        binding.emptyStateDescription.text = getString(R.string.empty_call_logs_permission_description)
        binding.grantPermissionButton.isVisible = true
    }

    private fun showEmptyState() {
        binding.recyclerView.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyStateTitle.text = getString(R.string.empty_call_logs_title)
        binding.emptyStateDescription.text = getString(R.string.empty_call_logs_description)
        binding.grantPermissionButton.isVisible = false
    }

    private fun showFilteredEmptyState() {
        binding.recyclerView.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyStateTitle.text = getString(R.string.empty_filtered_call_logs_title)
        binding.emptyStateDescription.text = getString(R.string.empty_filtered_call_logs_description)
        binding.grantPermissionButton.isVisible = false
    }

    private fun showCallLogs() {
        binding.recyclerView.isVisible = true
        binding.emptyState.isVisible = false
    }

    private fun applyFilters() {
        val filtered = allCallLogs.filter { item ->
            val matchesType = selectedCallType == null || item.callType == selectedCallType
            val matchesContact = when (selectedContactFilter) {
                ContactFilter.ALL -> true
                ContactFilter.CONTACTS_ONLY -> item.isContact
                ContactFilter.NON_CONTACTS_ONLY -> !item.isContact
            }
            matchesType && matchesContact
        }

        adapter.submitList(filtered)

        if (filtered.isEmpty() && allCallLogs.isNotEmpty()) {
            showFilteredEmptyState()
        } else if (filtered.isEmpty()) {
            showEmptyState()
        } else {
            showCallLogs()
        }
    }

    private fun lookupContactNumbers(numbers: Set<String>): Set<String> {
        val contactNumbers = mutableSetOf<String>()
        for (number in numbers) {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            try {
                requireContext().contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contactNumbers.add(number)
                    }
                }
            } catch (_: Exception) {
                // SecurityException if permission revoked, or other errors
            }
        }
        return contactNumbers
    }

    private fun loadCallLogs() {
        binding.callTypeFilterScroll.isVisible = true
        binding.contactFilterScroll.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val callLogs = withContext(Dispatchers.IO) {
                val results = mutableListOf<Triple<String, Long, Int>>()
                requireContext().contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.DATE,
                        CallLog.Calls.TYPE
                    ),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(0)
                        val date = cursor.getLong(1)
                        val type = cursor.getInt(2)
                        results.add(Triple(number, date, type))
                    }
                }

                val contactNumbers = if (hasContactsPermission()) {
                    val uniqueNumbers = results.map { it.first }.toSet()
                    lookupContactNumbers(uniqueNumbers)
                } else {
                    emptySet()
                }

                results.map { (number, date, type) ->
                    CallLogItem(
                        number = number,
                        date = date,
                        callType = type,
                        isContact = number in contactNumbers
                    )
                }
            }

            allCallLogs = callLogs
            applyFilters()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CallLogItem(
    val number: String,
    val date: Long,
    val callType: Int,
    val isContact: Boolean
)

class CallLogsAdapter(
    private val onItemClick: (String) -> Unit
) : ListAdapter<CallLogItem, CallLogsAdapter.ViewHolder>(CallLogDiffCallback()) {
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCallLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CallLogItem) {
            val number = item.number.trim()
            val (countryCode, phoneNumber) = PhoneNumberUtils.splitPhoneNumber(number)

            binding.countryCode.text = countryCode
            binding.phoneNumber.text = phoneNumber
            binding.callDate.text = dateFormat.format(Date(item.date))

            val (iconRes, descriptionRes) = when (item.callType) {
                CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_incoming to R.string.call_type_incoming
                CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing to R.string.call_type_outgoing
                CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed to R.string.call_type_missed
                else -> R.drawable.ic_call_incoming to R.string.call_type_incoming
            }
            binding.callTypeIcon.setImageResource(iconRes)
            binding.callTypeIcon.contentDescription = binding.root.context.getString(descriptionRes)

            val typeDesc = binding.root.context.getString(descriptionRes)
            binding.root.contentDescription =
                "$typeDesc from $countryCode $phoneNumber, ${binding.callDate.text}"

            binding.openChatButton.setOnClickListener {
                onItemClick(item.number)
            }
        }
    }
}

class CallLogDiffCallback : DiffUtil.ItemCallback<CallLogItem>() {
    override fun areItemsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
        return oldItem.number == newItem.number && oldItem.date == newItem.date
    }

    override fun areContentsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
        return oldItem == newItem
    }
}
