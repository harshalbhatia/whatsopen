package com.example.whatsopen

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
            val numbers = PhoneNumberUtils.parsePhoneNumbers(input)

            if (numbers.isEmpty()) {
                Toast.makeText(context, "No valid phone numbers found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Process first number
            WhatsAppLauncher.openChat(requireContext(), numbers.first())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
