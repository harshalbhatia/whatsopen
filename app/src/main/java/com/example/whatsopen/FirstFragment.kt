package com.example.whatsopen

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.whatsopen.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
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

        // Ensure the country code always starts with +
        binding.countryCodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.toString().startsWith("+")) {
                    binding.countryCodeInput.setText("+${s.toString().replace("+", "")}")
                    binding.countryCodeInput.setSelection(binding.countryCodeInput.text?.length ?: 0)
                }

                // Update country flag and name
                val numericCode = s.toString().replace("+", "")
                val countryInfo = COUNTRY_DATA[numericCode]
                (binding.countryCodeInputLayout as CountryCodeTextInputLayout)
                    .setCountryInfo(countryInfo?.flag, countryInfo?.name)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.openWhatsappButton.setOnClickListener {
            val countryCode = binding.countryCodeInput.text.toString().trim()
                .replace(Regex("[^0-9+]"), "") // Remove non-numeric characters except +
                .replace("+", "") // Remove + for the API call
            val phoneNumber = binding.phoneInput.text.toString().trim()
                .replace(Regex("[^0-9]"), "") // Remove non-numeric characters

            if (countryCode.isNotEmpty() && phoneNumber.isNotEmpty()) {
                val fullNumber = countryCode + phoneNumber
                WhatsAppLauncher.openChat(requireContext(), fullNumber)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
