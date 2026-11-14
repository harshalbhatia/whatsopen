package com.harshalbhatia.whatsopen

import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.harshalbhatia.whatsopen.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    /** The device's region: the default country code, and the country picked for a shared code like +1. */
    private var userRegion: String? = null

    /** Whether the user opened the message field. It stays open once opened. */
    private var messageShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageShown = savedInstanceState?.getBoolean(STATE_MESSAGE_SHOWN) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setAccessibilityPaneTitle(view, getString(R.string.nav_by_number))
        ViewCompat.setAccessibilityHeading(binding.title, true)

        // The keyboard shortens the tab (MainActivity pads it). The scroll view then scrolls the
        // focused field into view; this brings Start Chat along with it.
        binding.root.addOnLayoutChangeListener { scrollView, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top < oldBottom - oldTop) scrollView.post { revealFocusedFieldAndButton() }
        }

        userRegion = DefaultRegion.get(requireContext())

        binding.countryCodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCountryLabel()
                binding.countryCodeInputLayout.error = null
                // "Not a valid phone number" was about the number in this country; it may be valid now.
                binding.phoneInputLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.phoneInput.addTextChangedListener(object : TextWatcher {
            private var pasted = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.phoneInputLayout.error = null
                // Several characters at once: a paste or autofill, not typing.
                pasted = count > 1
            }
            override fun afterTextChanged(s: Editable) {
                val wasPasted = pasted
                pasted = false
                // Not while a saved value is being restored (the fragment isn't resumed then).
                if (wasPasted && isResumed && isInternational(s)) {
                    PhoneNumbers.normalize(s, userRegion)?.let(::showSplit)
                }
            }
        })

        // Restored state, applied after this, replaces the default.
        if (savedInstanceState == null && binding.countryCodeInput.text.isNullOrEmpty()) {
            userRegion?.let(CountryData::forRegion)?.let { binding.countryCodeInput.setText(it.callingCode) }
        }
        updateCountryLabel()

        binding.phoneInput.setOnEditorActionListener { _, actionId, event ->
            when {
                actionId == EditorInfo.IME_ACTION_GO -> {
                    submit()
                    true
                }
                // Enter on a hardware keyboard. Its key-up is consumed too, so it does nothing else.
                actionId == EditorInfo.IME_NULL && event != null && isEnterKey(event.keyCode) -> {
                    if (event.action == KeyEvent.ACTION_DOWN) submit()
                    true
                }
                else -> false
            }
        }

        showMessageField(messageShown)
        binding.addMessageButton.setOnClickListener {
            messageShown = true
            showMessageField(true)
            binding.messageInput.requestFocus()
            WindowInsetsControllerCompat(requireActivity().window, binding.messageInput)
                .show(WindowInsetsCompat.Type.ime())
        }

        binding.openWhatsappButton.setOnClickListener { submit() }
        binding.openWhatsappButton.setOnLongClickListener {
            val message = if (WhatsAppLauncher.clearRememberedApp(requireContext())) {
                R.string.app_choice_reset
            } else {
                R.string.app_choice_nothing_to_reset
            }
            showSnackbar(message)
            true
        }
        // Screen readers say "double-tap and hold to reset WhatsApp app choice".
        ViewCompat.replaceAccessibilityAction(
            binding.openWhatsappButton,
            AccessibilityActionCompat.ACTION_LONG_CLICK,
            getString(R.string.reset_app_choice_action),
            null,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_MESSAGE_SHOWN, messageShown)
    }

    private fun submit() {
        binding.countryCodeInputLayout.error = null
        binding.phoneInputLayout.error = null

        val phone = binding.phoneInput.text?.toString().orEmpty()
        val e164 = if (isInternational(phone)) {
            PhoneNumbers.normalize(phone, userRegion)?.also(::showSplit).also {
                if (it == null) binding.phoneInputLayout.error = getString(R.string.error_invalid_phone_number)
            }
        } else {
            numberFromFields(phone)
        } ?: return

        val message = binding.messageInput.text?.toString()?.takeIf { it.isNotBlank() }
        WhatsAppLauncher.openChat(requireContext(), e164, userRegion, message, onError = { showSnackbar(it) })
    }

    /** The number in the two fields, as E.164, or null after showing an error on the field at fault. */
    private fun numberFromFields(phone: String): String? {
        val code = asciiDigits(binding.countryCodeInput.text)
        var valid = true
        if (code.isEmpty()) {
            binding.countryCodeInputLayout.error = getString(R.string.error_country_code_required)
            valid = false
        }
        if (asciiDigits(phone).isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.error_phone_required)
            valid = false
        }
        if (!valid) return null

        val region = CountryData.forCallingCode(code, userRegion)?.regionCode
        val e164 = chatNumber(code, phone, region)
        when {
            e164 != null -> Unit
            region == null -> binding.countryCodeInputLayout.error = getString(R.string.unknown_country_code)
            else -> binding.phoneInputLayout.error = getString(R.string.error_invalid_phone_number)
        }
        return e164
    }

    /** Shows [e164] over the two fields: the country code in its field, the rest in the number field. */
    private fun showSplit(e164: String) {
        val (code, national) = PhoneNumbers.splitPhoneNumber(e164)
        // A code no country uses (+800, +882, …) has no field to go in; the number stays whole.
        if (code.isEmpty()) return
        binding.countryCodeInput.setText(code.removePrefix("+"))
        val phone = binding.phoneInput.text ?: return
        phone.replace(0, phone.length, national)
        Selection.setSelection(phone, phone.length)
    }

    private fun updateCountryLabel() {
        val label = binding.countryInfo
        val code = asciiDigits(binding.countryCodeInput.text)
        val info = CountryData.forCallingCode(code, userRegion)
        when {
            info != null -> {
                label.text = getString(R.string.country_label, info.flag, info.name)
                label.contentDescription = getString(R.string.country_label_description, info.name)
            }
            // Only once more digits can't help: "9" may become "91", "999" never will.
            code.isNotEmpty() && CountryData.all.none { it.callingCode.startsWith(code) } -> {
                label.text = getString(R.string.unknown_country_code)
                label.contentDescription = null
            }
            else -> {
                label.text = null
                label.contentDescription = null
            }
        }
    }

    /**
     * Scrolls so the focused field and the Start Chat button below it both show, or if they
     * don't fit, the top of the field.
     */
    private fun revealFocusedFieldAndButton() {
        val binding = _binding ?: return
        val scrollView = binding.root
        val focused = scrollView.findFocus()?.takeIf { it !== scrollView } ?: return
        val content = scrollView.getChildAt(0) as ViewGroup
        val area = Rect().also { focused.getDrawingRect(it) }
        content.offsetDescendantRectToMyCoords(focused, area)
        val button = Rect().also { binding.openWhatsappButton.getDrawingRect(it) }
        content.offsetDescendantRectToMyCoords(binding.openWhatsappButton, button)
        area.union(button)
        scrollView.requestChildRectangleOnScreen(content, area, false)
    }

    private fun showMessageField(shown: Boolean) {
        binding.addMessageButton.isVisible = !shown
        binding.messageInputLayout.isVisible = shown
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STATE_MESSAGE_SHOWN = "message_shown"

        /**
         * The E.164 number for a calling code ("44") and a number typed without it, or null if
         * that isn't a valid number. [national] is read as a number of [region] first, so trunk
         * prefixes work ("07911 123456" with 44 gives +447911123456); failing that, as
         * "+[callingCode][national]", which also accepts numbers the platform's metadata rejects.
         * The fallback refuses a number starting with 0: whether that 0 is a trunk prefix
         * (07700… in the UK) or part of the number (06… in Italy) is then unknown, and guessing
         * could open a stranger's chat. Typed with "+" instead, such a number is accepted.
         */
        internal fun chatNumber(callingCode: String, national: String, region: String?): String? {
            if (region != null) {
                PhoneNumbers.normalize(national, region)
                    // Digits that only make sense as another country's number aren't what was meant.
                    ?.takeIf { it.startsWith("+$callingCode") }
                    ?.let { return it }
            }
            val digits = asciiDigits(national)
            if (digits.startsWith("0")) return null
            return PhoneNumbers.normalize("+$callingCode$digits", region)
        }

        /** Whether [text] starts with "+" or "00", i.e. includes its country code. */
        private fun isInternational(text: CharSequence): Boolean {
            val start = PhoneNumbers.sanitize(text).trimStart(' ', '(')
            return start.startsWith("+") || start.startsWith("00")
        }

        /** The digits in [text], in any script, as ASCII. */
        private fun asciiDigits(text: CharSequence?): String =
            PhoneNumbers.sanitize(text ?: "").filter { it in '0'..'9' }

        private fun isEnterKey(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }
}
