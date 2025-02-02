package com.example.whatsopen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.whatsopen.databinding.FragmentCallLogsBinding
import com.example.whatsopen.databinding.ItemCallLogBinding
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
            openInWhatsApp(number)
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
                // Show explanation if needed
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
        val callLogs = mutableListOf<CallLogItem>()
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
                callLogs.add(CallLogItem(number, date))
            }
        }
        adapter.submitList(callLogs)
    }

    private fun openInWhatsApp(number: String) {
        try {
            try {
                requireContext().packageManager.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
                openWithPackage(number, "com.whatsapp")
            } catch (e: PackageManager.NameNotFoundException) {
                try {
                    requireContext().packageManager.getPackageInfo("com.whatsapp.w4b", PackageManager.GET_ACTIVITIES)
                    openWithPackage(number, "com.whatsapp.w4b")
                } catch (e: PackageManager.NameNotFoundException) {
                    Toast.makeText(
                        requireContext(),
                        "WhatsApp is not installed on your device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error opening WhatsApp",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openWithPackage(number: String, packageName: String) {
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$number")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
        }
        startActivity(intent)
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
) : RecyclerView.Adapter<CallLogsAdapter.ViewHolder>() {
    private var items = listOf<CallLogItem>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun submitList(newItems: List<CallLogItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemCallLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CallLogItem) {
            // Split the number into country code and number
            val number = item.number.trim()
            val countryCode = number.substring(0, number.length - 10)
            val phoneNumber = number.substring(number.length - 10)

            binding.countryCode.text = countryCode
            binding.phoneNumber.text = phoneNumber
            binding.callDate.text = dateFormat.format(Date(item.date))
            binding.openChatButton.setOnClickListener {
                onItemClick(item.number)
            }
        }
    }
}
