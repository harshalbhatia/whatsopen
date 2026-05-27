package com.harshalbhatia.whatsopen

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import com.harshalbhatia.whatsopen.databinding.FragmentCallLogsBinding
import com.harshalbhatia.whatsopen.databinding.ItemCallLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallLogsFragment : Fragment() {
    private var _binding: FragmentCallLogsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CallLogsAdapter

    private var allCallLogs: List<CallLogItem> = emptyList()
    private var filterIncoming = false
    private var filterMissed = false
    private var filterNonContactsOnly = false

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callLogGranted = permissions[Manifest.permission.READ_CALL_LOG] == true
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        if (callLogGranted) {
            updateNonContactsChipState(contactsGranted)
            loadCallLogs()
        } else {
            showPermissionDeniedState()
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.chipNonContactsOnly.isEnabled = true
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

        binding.chipIncoming.setOnCheckedChangeListener { _, isChecked ->
            filterIncoming = isChecked
            applyFilters()
        }

        binding.chipMissed.setOnCheckedChangeListener { _, isChecked ->
            filterMissed = isChecked
            applyFilters()
        }

        binding.chipNonContactsOnly.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasContactsPermission()) {
                binding.chipNonContactsOnly.isChecked = false
                requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                return@setOnCheckedChangeListener
            }
            filterNonContactsOnly = isChecked
            applyFilters()
        }

        binding.grantPermissionButton.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                requestPermissionsLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
                )
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
        }

        checkPermissionsAndLoadLogs()
    }

    private fun checkPermissionsAndLoadLogs() {
        val hasCallLog = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallLog) {
            updateNonContactsChipState(hasContactsPermission())
            loadCallLogs()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
            showPermissionDeniedState()
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            )
        }
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateNonContactsChipState(hasPermission: Boolean) {
        binding.chipNonContactsOnly.isEnabled = hasPermission
        if (!hasPermission) {
            binding.chipNonContactsOnly.isChecked = false
            filterNonContactsOnly = false
        }
    }

    private fun showPermissionDeniedState() {
        binding.recyclerView.isVisible = false
        binding.filterScroll.isVisible = false
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
            val matchesType = when {
                filterIncoming && filterMissed ->
                    item.callType == CallLog.Calls.INCOMING_TYPE || item.callType == CallLog.Calls.MISSED_TYPE
                filterIncoming -> item.callType == CallLog.Calls.INCOMING_TYPE
                filterMissed -> item.callType == CallLog.Calls.MISSED_TYPE
                else -> true
            }
            val matchesContact = !filterNonContactsOnly || !item.isContact
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

    private fun lookupContacts(context: Context, numbers: Set<String>): Map<String, String?> {
        val results = mutableMapOf<String, String?>()
        for (number in numbers) {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            try {
                context.contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        results[number] = if (name.isNullOrBlank()) null else name
                    }
                }
            } catch (_: Exception) {
                // SecurityException if permission revoked, or other errors
            }
        }
        return results
    }

    private fun loadCallLogs() {
        binding.filterScroll.isVisible = true

        val appContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            val callLogs = withContext(Dispatchers.IO) {
                val results = mutableListOf<Triple<String, Long, Int>>()
                queryCallLog(appContext.contentResolver)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(0)
                        val date = cursor.getLong(1)
                        val type = cursor.getInt(2)
                        results.add(Triple(number, date, type))
                    }
                }

                val contactMap = if (hasContactsPermission()) {
                    val uniqueNumbers = results.map { it.first }.toSet()
                    lookupContacts(appContext, uniqueNumbers)
                } else {
                    emptyMap()
                }

                results.map { (number, date, type) ->
                    CallLogItem(
                        number = number,
                        date = date,
                        callType = type,
                        isContact = contactMap.containsKey(number),
                        contactName = contactMap[number]
                    )
                }
            }

            allCallLogs = callLogs
            applyFilters()
        }
    }

    private fun queryCallLog(resolver: ContentResolver) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val args = Bundle().apply {
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(CallLog.Calls.DATE)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putInt(ContentResolver.QUERY_ARG_LIMIT, CALL_LOG_LIMIT)
            }
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                args,
                null
            )
        } else {
            resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $CALL_LOG_LIMIT"
            )
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CALL_LOG_LIMIT = 200
    }
}

data class CallLogItem(
    val number: String,
    val date: Long,
    val callType: Int,
    val isContact: Boolean,
    val contactName: String?
)

class CallLogsAdapter(
    private val onItemClick: (String) -> Unit
) : ListAdapter<CallLogItem, CallLogsAdapter.ViewHolder>(CallLogDiffCallback()) {

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
            val context = binding.root.context
            val cleanedNumber = item.number.trim().replace(Regex("[^0-9+]"), "")
            val (countryCode, phoneNumber) = PhoneNumberUtils.splitPhoneNumber(cleanedNumber)

            binding.countryCode.text = countryCode
            binding.countryCode.isVisible = countryCode.isNotEmpty()
            binding.phoneNumber.text = phoneNumber
            binding.callDate.text = DateFormatter.format(context, item.date)

            if (item.contactName != null) {
                binding.contactName.text = item.contactName
                binding.contactName.isVisible = true
            } else {
                binding.contactName.isVisible = false
            }

            val (iconRes, descriptionRes) = when (item.callType) {
                CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_incoming to R.string.call_type_incoming
                CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing to R.string.call_type_outgoing
                CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed to R.string.call_type_missed
                else -> R.drawable.ic_call_incoming to R.string.call_type_incoming
            }
            binding.callTypeIcon.setImageResource(iconRes)

            val typeDesc = context.getString(descriptionRes)
            val identity = item.contactName ?: "$countryCode $phoneNumber".trim()
            binding.root.contentDescription = "$typeDesc from $identity, ${binding.callDate.text}"

            val open = View.OnClickListener { onItemClick(item.number) }
            binding.callLogCard.setOnClickListener(open)
            binding.openChatButton.setOnClickListener(open)
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
