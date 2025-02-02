package com.example.whatsopen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.whatsopen.databinding.FragmentFirstBinding
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Data class to hold country information
    data class CountryInfo(val flag: String, val name: String)

    // Updated map to include country names
    private val countryData = mapOf(
        "1" to CountryInfo("🇺🇸", "United States"),
        "44" to CountryInfo("🇬🇧", "United Kingdom"),
        "91" to CountryInfo("🇮🇳", "India"),
        "86" to CountryInfo("🇨🇳", "China"),
        "81" to CountryInfo("🇯🇵", "Japan"),
        "49" to CountryInfo("🇩🇪", "Germany"),
        "33" to CountryInfo("🇫🇷", "France"),
        "39" to CountryInfo("🇮🇹", "Italy"),
        "7" to CountryInfo("🇷🇺", "Russia"),
        "55" to CountryInfo("🇧🇷", "Brazil"),
        "34" to CountryInfo("🇪🇸", "Spain"),
        "61" to CountryInfo("🇦🇺", "Australia"),
        "52" to CountryInfo("🇲🇽", "Mexico"),
        "82" to CountryInfo("🇰🇷", "South Korea"),
        "90" to CountryInfo("🇹🇷", "Turkey"),
        "31" to CountryInfo("🇳🇱", "Netherlands"),
        "966" to CountryInfo("🇸🇦", "Saudi Arabia"),
        "971" to CountryInfo("🇦🇪", "UAE"),
        "65" to CountryInfo("🇸🇬", "Singapore"),
        "92" to CountryInfo("🇵🇰", "Pakistan"),
        "351" to CountryInfo("🇵🇹", "Portugal"),
        "54" to CountryInfo("🇦🇷", "Argentina"),
        "20" to CountryInfo("🇪🇬", "Egypt"),
        "27" to CountryInfo("🇿🇦", "South Africa"),
        "30" to CountryInfo("🇬🇷", "Greece"),
        "32" to CountryInfo("🇧🇪", "Belgium"),
        "36" to CountryInfo("🇭🇺", "Hungary"),
        "40" to CountryInfo("🇷🇴", "Romania"),
        "41" to CountryInfo("🇨🇭", "Switzerland"),
        "43" to CountryInfo("🇦🇹", "Austria"),
        "45" to CountryInfo("🇩🇰", "Denmark"),
        "46" to CountryInfo("🇸🇪", "Sweden"),
        "47" to CountryInfo("🇳🇴", "Norway"),
        "48" to CountryInfo("🇵🇱", "Poland"),
        "51" to CountryInfo("🇵🇪", "Peru"),
        "53" to CountryInfo("🇨🇺", "Cuba"),
        "56" to CountryInfo("🇨🇱", "Chile"),
        "57" to CountryInfo("🇨🇴", "Colombia"),
        "58" to CountryInfo("🇻🇪", "Venezuela"),
        "60" to CountryInfo("🇲🇾", "Malaysia"),
        "62" to CountryInfo("🇮🇩", "Indonesia"),
        "63" to CountryInfo("🇵🇭", "Philippines"),
        "64" to CountryInfo("🇳🇿", "New Zealand"),
        "66" to CountryInfo("🇹🇭", "Thailand"),
        "84" to CountryInfo("🇻🇳", "Vietnam"),
        "93" to CountryInfo("🇦🇫", "Afghanistan"),
        "94" to CountryInfo("🇱🇰", "Sri Lanka"),
        "95" to CountryInfo("🇲🇲", "Myanmar"),
        "98" to CountryInfo("🇮🇷", "Iran"),
        "212" to CountryInfo("🇲🇦", "Morocco"),
        "213" to CountryInfo("🇩🇿", "Algeria"),
        "216" to CountryInfo("🇹🇳", "Tunisia"),
        "218" to CountryInfo("🇱🇾", "Libya"),
        "220" to CountryInfo("🇬🇲", "Gambia"),
        "234" to CountryInfo("🇳🇬", "Nigeria"),
        "254" to CountryInfo("🇰🇪", "Kenya"),
        "263" to CountryInfo("🇿🇼", "Zimbabwe"),
        "355" to CountryInfo("🇦🇱", "Albania"),
        "380" to CountryInfo("🇺🇦", "Ukraine"),
        "420" to CountryInfo("🇨🇿", "Czech Republic"),
        "421" to CountryInfo("🇸🇰", "Slovakia"),
        "886" to CountryInfo("🇹🇼", "Taiwan")
    )

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
                val countryInfo = countryData[numericCode]
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
                openInWhatsApp(fullNumber)
            }
        }
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
