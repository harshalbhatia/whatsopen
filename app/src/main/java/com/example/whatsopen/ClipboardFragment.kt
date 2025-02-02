package com.example.whatsopen

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.whatsopen.databinding.FragmentClipboardBinding

class ClipboardFragment : Fragment() {
    private var _binding: FragmentClipboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClipboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.processNumbersButton.setOnClickListener {
            val input = binding.numbersInput.text.toString().trim()
            val numbers = parsePhoneNumbers(input)

            if (numbers.isEmpty()) {
                Toast.makeText(context, "No valid phone numbers found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Process first number
            openInWhatsApp(numbers.first())
        }
    }

    private fun parsePhoneNumbers(input: String): List<String> {
        return input.split("\n", ",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("[^0-9+]"), "") }
            .filter { it.length >= 10 }
    }

    private fun openInWhatsApp(number: String) {
        try {
            // Try regular WhatsApp first
            try {
                requireContext().packageManager.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
                openWithPackage(number, "com.whatsapp")
            } catch (e: PackageManager.NameNotFoundException) {
                // If regular WhatsApp not found, try WhatsApp Business
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
