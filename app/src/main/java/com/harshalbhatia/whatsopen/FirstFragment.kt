package com.harshalbhatia.whatsopen

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import com.harshalbhatia.whatsopen.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.countryCodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val digits = s?.toString().orEmpty().filter { it.isDigit() }
                val info = COUNTRY_DATA[digits]
                binding.countryInfo.text = if (info != null) "${info.flag}  ${info.name}" else null
                binding.countryCodeInputLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.phoneInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.phoneInputLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.phoneInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                binding.openWhatsappButton.performClick()
                true
            } else false
        }

        binding.openWhatsappButton.setOnClickListener {
            binding.countryCodeInputLayout.error = null
            binding.phoneInputLayout.error = null

            val countryCode = binding.countryCodeInput.text.toString()
                .replace(Regex("[^0-9]"), "")
            val phoneNumber = binding.phoneInput.text.toString()
                .replace(Regex("[^0-9]"), "")

            var hasError = false
            if (countryCode.isEmpty()) {
                binding.countryCodeInputLayout.error = getString(R.string.error_country_code_required)
                hasError = true
            }
            if (phoneNumber.isEmpty()) {
                binding.phoneInputLayout.error = getString(R.string.error_phone_required)
                hasError = true
            }

            if (!hasError) {
                WhatsAppLauncher.openChat(requireContext(), countryCode + phoneNumber)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
