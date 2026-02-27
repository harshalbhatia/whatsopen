package com.example.whatsopen

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.numbersInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.numbersInputLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.processNumbersButton.setOnClickListener {
            binding.numbersInputLayout.error = null

            val input = binding.numbersInput.text.toString().trim()
            val numbers = PhoneNumberUtils.parsePhoneNumbers(input)

            if (numbers.isEmpty()) {
                binding.numbersInputLayout.error = getString(R.string.error_no_valid_numbers)
                return@setOnClickListener
            }

            WhatsAppLauncher.openChat(requireContext(), numbers.first())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
