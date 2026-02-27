package com.example.whatsopen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadCallLogs()
        } else {
            Toast.makeText(context, "Permission required to show call logs", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(
                    context,
                    "Call log permission is needed to show recent calls",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
    }

    private fun loadCallLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val callLogs = withContext(Dispatchers.IO) {
                val results = mutableListOf<CallLogItem>()
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
                        results.add(CallLogItem(number, date))
                    }
                }
                results
            }
            adapter.submitList(callLogs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CallLogItem(
    val number: String,
    val date: Long
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
