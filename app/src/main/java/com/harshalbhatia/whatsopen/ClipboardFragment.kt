package com.harshalbhatia.whatsopen

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.text.BidiFormatter
import android.text.Editable
import android.text.InputFilter
import android.text.TextDirectionHeuristics
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.textclassifier.TextClassifier
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.harshalbhatia.whatsopen.databinding.FragmentClipboardBinding

/**
 * Finds the phone numbers in pasted or shared text and opens a chat with one.
 *
 * The clipboard is only read when the user taps Paste. Until then the tab only asks Android
 * whether the clipboard holds text, which doesn't show Android 12's "pasted from your
 * clipboard" message, and offers a Paste card if it does.
 */
class ClipboardFragment : Fragment() {
    private var _binding: FragmentClipboardBinding? = null
    private val binding get() = _binding!!

    private var region: String? = null

    /** The numbers in the text box, as of the last [showResults]. */
    private var numbers: List<String> = emptyList()

    /** When the clip the user last pasted (or tried to) was copied. Its card isn't offered again. */
    private var pastedClipTimestamp: Long? = null

    private val showResultsRunnable = Runnable { showResults() }

    private val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && isResumed) updateSuggestion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pastedClipTimestamp = savedInstanceState?.takeIf { it.containsKey(STATE_PASTED_CLIP) }
            ?.getLong(STATE_PASTED_CLIP)
    }

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

        region = DefaultRegion.get(requireContext())

        binding.numbersInput.filters = arrayOf(InputFilter.LengthFilter(PhoneNumbers.MAX_TEXT_CHARS))
        binding.numbersInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.numbersInputLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {
                // Searching a long text on every keystroke would lag, so wait for a pause in typing.
                binding.root.removeCallbacks(showResultsRunnable)
                binding.root.postDelayed(showResultsRunnable, RESULTS_DELAY_MS)
            }
        })
        allowScrollingInside(binding.numbersInput)

        binding.numbersInputLayout.setEndIconOnClickListener { paste() }
        binding.clipboardPasteButton.setOnClickListener { paste() }

        binding.processNumbersButton.setOnClickListener {
            showResults()
            val number = numbers.singleOrNull()
            if (number != null) {
                openChat(number)
            } else {
                binding.numbersInputLayout.error = getString(R.string.no_phone_number_found)
                // The error says it; the line below the box would repeat it.
                binding.resultsCount.isVisible = false
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Restored text shows its numbers straight away.
        showResults()
    }

    override fun onResume() {
        super.onResume()
        // Shared text replaces whatever is in the box; it's what the user just asked to see.
        PendingShare.consume()?.takeUnless { it.isBlank() }?.let(::showText)

        requireActivity().window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)
        // Android 10+ only lets an app with window focus see the clipboard. Without focus yet
        // (e.g. just launched), the listener checks once it arrives.
        if (requireView().hasWindowFocus()) updateSuggestion()
    }

    override fun onPause() {
        super.onPause()
        requireActivity().window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pastedClipTimestamp?.let { outState.putLong(STATE_PASTED_CLIP, it) }
    }

    /**
     * Shows the Paste card if the clipboard holds text the user hasn't pasted here yet. Looks
     * only at the clip's description (type, time, Android's guess at its content), never its text.
     */
    private fun updateSuggestion() {
        val description = clipboard()?.takeIf { it.hasPrimaryClip() }?.primaryClipDescription
        // Without a timestamp (before Android 8) the card can come back for a clip already pasted.
        val alreadyPasted = description != null && clipTimestamp(description)?.let { it == pastedClipTimestamp } == true
        // Sensitive clips (Android 13+: passwords and the like) are offered too: the user may
        // well have copied a number from a banking or messaging app that flags everything.
        val offer = description != null && description.hasMimeType("text/*") && !alreadyPasted
        binding.clipboardSuggestion.isVisible = offer
        if (offer) {
            binding.clipboardSuggestionText.setText(
                if (looksLikePhoneNumber(description!!)) {
                    R.string.clipboard_suggestion_text_phone
                } else {
                    R.string.clipboard_suggestion_text
                }
            )
        }
    }

    /** Reads the clipboard into the text box. Only ever called from a tap on Paste. */
    private fun paste() {
        binding.clipboardSuggestion.isVisible = false
        val clipboard = clipboard() ?: return
        val clip = readPrimaryClip(clipboard)
        clip?.description?.let { pastedClipTimestamp = clipTimestamp(it) }
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(requireContext())
        if (text.isNullOrBlank()) {
            showSnackbar(R.string.error_clipboard_empty)
            return
        }
        showText(text)
    }

    /** Replaces the text box's content with [text] and shows its numbers without waiting. */
    private fun showText(text: CharSequence) {
        binding.numbersInput.setText(PhoneNumbers.capText(text))
        binding.numbersInput.setSelection(binding.numbersInput.length())
        showResults()
    }

    /** Finds the numbers in the text box and shows them: a count, then the number or a chip for each. */
    private fun showResults() {
        val binding = _binding ?: return
        binding.root.removeCallbacks(showResultsRunnable)
        val text = binding.numbersInput.text ?: ""
        numbers = if (text.isBlank()) emptyList() else PhoneNumbers.extract(text, region)

        binding.resultsCount.isVisible = text.isNotBlank()
        binding.resultsCount.text = if (numbers.isEmpty()) {
            getString(R.string.no_phone_number_found)
        } else {
            resources.getQuantityString(R.plurals.numbers_found, numbers.size, numbers.size)
        }

        binding.singleNumber.isVisible = numbers.size == 1
        binding.singleNumber.text = numbers.singleOrNull()?.let(::displayNumber)

        // Several numbers: each chip opens its own chat, so there's no single button to press.
        binding.processNumbersButton.isVisible = numbers.size <= 1
        binding.numbersChipGroup.isVisible = numbers.size > 1
        binding.numbersChipGroup.removeAllViews()
        if (numbers.size > 1) {
            numbers.forEach { binding.numbersChipGroup.addView(chipFor(it)) }
        }
    }

    private fun chipFor(e164: String): Chip = Chip(requireContext()).apply {
        text = displayNumber(e164)
        setOnClickListener { openChat(e164) }
        // Screen readers say "double-tap to start chat in WhatsApp".
        ViewCompat.replaceAccessibilityAction(
            this, AccessibilityActionCompat.ACTION_CLICK, getString(R.string.open_in_whatsapp), null
        )
    }

    private fun openChat(e164: String) {
        WhatsAppLauncher.openChat(requireContext(), e164, region, onError = { showSnackbar(it) })
    }

    /** [e164] formatted for reading ("+44 7911 123456"), kept left to right in right-to-left text. */
    private fun displayNumber(e164: String): String {
        val formatted = PhoneNumberUtils.formatNumber(e164, region ?: "") ?: e164
        return BidiFormatter.getInstance().unicodeWrap(formatted, TextDirectionHeuristics.LTR)
    }

    private fun clipboard(): ClipboardManager? =
        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    /**
     * Lets the text box scroll its own text once it is taller than its 8 lines; otherwise the
     * page's scroll view takes every vertical drag. The touch still goes on to the text box,
     * which handles clicks and accessibility itself, so there is nothing to call performClick for.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun allowScrollingInside(view: View) {
        view.setOnTouchListener { v, event ->
            if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                val done = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
                v.parent.requestDisallowInterceptTouchEvent(!done)
            }
            false
        }
    }

    override fun onDestroyView() {
        binding.root.removeCallbacks(showResultsRunnable)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STATE_PASTED_CLIP = "pasted_clip_timestamp"

        /** How long typing must pause before the text is searched again. */
        @VisibleForTesting
        internal const val RESULTS_DELAY_MS = 300L

        /** At least this confident that the clip is a phone number, the card says so. */
        private const val PHONE_CONFIDENCE = 0.5f

        /** Reads the clipboard's content. Replaceable in tests, to check when the clipboard is read. */
        @VisibleForTesting
        internal var readPrimaryClip: (ClipboardManager) -> ClipData? = ClipboardManager::getPrimaryClip

        /** When the clip was copied, which tells two clips apart without reading them. Null if unknown. */
        private fun clipTimestamp(description: ClipDescription): Long? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) description.timestamp.takeIf { it != 0L } else null

        /** Whether Android (12+) classified the clip as most likely a phone number. */
        private fun looksLikePhoneNumber(description: ClipDescription): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            if (description.classificationStatus != ClipDescription.CLASSIFICATION_COMPLETE) return false
            return description.getConfidenceScore(TextClassifier.TYPE_PHONE) >= PHONE_CONFIDENCE
        }
    }
}
