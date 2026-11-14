package com.harshalbhatia.whatsopen

import android.telephony.PhoneNumberUtils as PlatformPhoneNumberUtils

/**
 * Finds phone numbers in text and converts them to E.164 ("+14155552671").
 *
 * Validation uses the platform's copy of libphonenumber
 * ([PlatformPhoneNumberUtils.formatNumberToE164]), so it is only as current as the
 * device's number metadata. [normalize] documents the fallback for numbers it rejects.
 */
object PhoneNumbers {

    /** E.164 allows at most 15 digits, country code included. */
    const val MAX_E164_DIGITS = 15

    /** Free text is cut to this many characters before it is searched. */
    const val MAX_TEXT_CHARS = 4096

    /** Shortest "+" number the lenient fallback accepts, country code included. */
    private const val MIN_LENIENT_DIGITS = 8

    /** Digit groups shorter than this are never taken from free text as a phone number. */
    private const val MIN_EXTRACTED_DIGITS = 7

    /** A candidate in free text may carry an international prefix such as 0011 on top of 15 digits. */
    private const val MAX_DIALLED_DIGITS = MAX_E164_DIGITS + 4

    /** libphonenumber's code for "no region": only numbers with a country code can parse. */
    private const val UNKNOWN_REGION = "ZZ"

    /** A newline, comma or semicolon always ends a number, in any script. */
    private val HARD_SEPARATORS = Regex("[\\n\\r\\u000B\\u000C\\u0085\\u2028\\u2029,;\\u060C\\u061B\\u3001\\uFF0C\\uFF1B]")

    /**
     * A stretch of text that could be one or more phone numbers: digits with spaces, dashes,
     * dots, brackets or slashes between them, optionally led by "+" or "(". A "+" can't occur
     * inside a run, so "+91… +1…" is two runs.
     */
    private val RUN = Regex("""\(?\+?\(?\d(?:[\d ().\-/]*\d)?""")

    /** Links are removed before searching, so IDs inside them aren't mistaken for numbers. */
    private val URL = Regex("""(?i)\b(?:[a-z][a-z\d+.\-]*://|www\.)\S*""")

    /** yyyy-mm-dd, dd.mm.yyyy or mm/dd/yyyy; checked for plausible day and month in [isDate]. */
    private val DATE = Regex("""(\d{4})[./\-](\d{1,2})[./\-](\d{1,2})|(\d{1,2})[./\-](\d{1,2})[./\-](\d{4})""")

    /** Digits and visual separators, not starting with 0: may be a number written without its "+". */
    private val BARE_INTERNATIONAL_SHAPE = Regex("""\(?[1-9][\d ().\-/]*""")

    /**
     * A whole +1 number: the North American plan has exactly 10 digits after the code, and
     * area codes never start with 0 or 1. Without this, "+1 1 416 555 0123" (the trunk "1"
     * repeated) would pass as a 12-digit +1 number.
     */
    private val NANP_NUMBER = Regex("""1[2-9]\d{9}""")

    /** Only "+", digits and visual separators: the shape a written "+" number has. */
    private val PLUS_NUMBER_SHAPE = Regex("""\(?\+[\d ().\-/]*""")

    /**
     * Converts one written phone number to E.164, or returns null if it isn't a valid number.
     *
     * - Numbers without a country code are read as national numbers of [defaultRegion]
     *   (an ISO 3166 code, any case). Trunk prefixes ("07700…", "8 916…") and the region's
     *   international prefix ("011 44…" in the US) are handled by the platform.
     * - If that fails, digits that start with a country code but no "+" ("919876543210",
     *   as wa.me links write them) are read as an international number.
     * - A leading "00" is also read as "+", even where the region dials abroad differently.
     * - Unicode digits (Arabic-Indic, Persian, Devanagari, …) count as digits, and invisible
     *   formatting characters (bidi controls, zero-width spaces) are ignored.
     * - The platform's validation can be stricter than reality (old metadata, fictional
     *   ranges like 555), so a number written with "+" is still accepted when it has 8–15
     *   digits and starts with an assigned country code (for +1, exactly 10 more digits).
     *   A "(0)" after the country code is dropped in that case.
     */
    fun normalize(raw: CharSequence, defaultRegion: String?): String? {
        val text = sanitize(raw).trim()
        if (text.isEmpty()) return null
        val region = regionOrUnknown(defaultRegion)
        return strictE164(text, region) ?: bareInternationalE164(text, region) ?: lenientE164(text)
    }

    /**
     * Finds every phone number in free text (clipboard, shared or selected text), in order,
     * as distinct E.164 strings. Uses the same rules as [normalize].
     *
     * Numbers may be written with or without spaces, dashes, dots, brackets or slashes.
     * A newline, comma or semicolon always separates numbers, and so does a "+".
     * Space-separated digit groups are joined into the longest valid number, so
     * "+91 98765 43210" is one number while "4155552671 4155552672" is two.
     * Digits stuck to letters or a "#" ("Order #12345678", "AB12345678"), dates and links
     * are skipped. Only the first [MAX_TEXT_CHARS] characters are searched.
     */
    fun extract(text: CharSequence, defaultRegion: String?): List<String> {
        val clean = URL.replace(sanitize(capText(text)), " ")
        val region = regionOrUnknown(defaultRegion)
        val found = LinkedHashSet<String>()
        for (segment in clean.split(HARD_SEPARATORS)) {
            for (run in RUN.findAll(segment)) {
                if (isStuckToWord(segment, run.range)) continue
                collectNumbers(run.value, region, found)
            }
        }
        return found.toList()
    }

    /**
     * Splits a "+" phone number into (countryCode, localNumber), e.g. ("+1", "4155552671").
     * Uses the calling code from [CountryData] that the digits start with.
     * Falls back to ("", trimmed) if there's no "+", no code matches, or input is too short.
     */
    fun splitPhoneNumber(number: String): Pair<String, String> {
        val trimmed = number.trim()
        val digits = trimmed.replace(Regex("[^0-9]"), "")
        if (!trimmed.startsWith("+") || digits.length < 7) {
            return Pair("", trimmed)
        }
        val code = CountryData.callingCodePrefix(digits)
        if (code == null || digits.length <= code.length) {
            return Pair("", trimmed)
        }
        return Pair("+$code", digits.substring(code.length))
    }

    /**
     * Converts every Unicode decimal digit to ASCII, drops invisible formatting characters
     * (Unicode category Cf: bidi controls such as U+202E, zero-width spaces and joiners),
     * and maps Unicode spaces, dashes and full-width plus signs to their ASCII forms.
     */
    internal fun sanitize(text: CharSequence): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            val digit = Character.digit(cp, 10)
            val type = Character.getType(cp)
            when {
                digit >= 0 -> out.append('0' + digit)
                type == Character.FORMAT.toInt() -> Unit
                cp == FULLWIDTH_PLUS || cp == SMALL_PLUS -> out.append('+')
                type == Character.SPACE_SEPARATOR.toInt() || cp == '\t'.code -> out.append(' ')
                type == Character.DASH_PUNCTUATION.toInt() || cp == MINUS_SIGN -> out.append('-')
                else -> out.appendCodePoint(cp)
            }
        }
        return out.toString()
    }

    /** Cuts [text] to [MAX_TEXT_CHARS] characters without splitting a surrogate pair. */
    internal fun capText(text: CharSequence): String {
        if (text.length <= MAX_TEXT_CHARS) return text.toString()
        var end = MAX_TEXT_CHARS
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.subSequence(0, end).toString()
    }

    private fun regionOrUnknown(region: String?): String = DefaultRegion.cleanIso(region) ?: UNKNOWN_REGION

    /** Platform validation, retrying a leading "00" as "+" for regions that dial abroad differently. */
    private fun strictE164(text: String, region: String): String? {
        PlatformPhoneNumberUtils.formatNumberToE164(text, region)?.let { return it.takeIf(::fitsE164) }
        val afterBracket = text.trimStart('(', ' ')
        if (!afterBracket.startsWith("00")) return null
        return PlatformPhoneNumberUtils.formatNumberToE164("+" + afterBracket.substring(2), region)
            ?.takeIf(::fitsE164)
    }

    /**
     * Reads digits without "+" as an international number ("919876543210" → +919876543210).
     * Only tried after [strictE164], so a valid national number always wins.
     */
    private fun bareInternationalE164(text: String, region: String): String? {
        if (!BARE_INTERNATIONAL_SHAPE.matches(text)) return null
        return PlatformPhoneNumberUtils.formatNumberToE164("+" + text.filter(::isAsciiDigit), region)
            ?.takeIf(::fitsE164)
    }

    /** Accepts "+" numbers the platform rejects; see [normalize]. */
    private fun lenientE164(text: String): String? {
        if (!PLUS_NUMBER_SHAPE.matches(text)) return null
        val digits = text.replace("(0)", "").filter(::isAsciiDigit)
        if (digits.length !in MIN_LENIENT_DIGITS..MAX_E164_DIGITS) return null
        val code = CountryData.callingCodePrefix(digits) ?: return null
        if (code == NANP_CALLING_CODE && !NANP_NUMBER.matches(digits)) return null
        return "+$digits"
    }

    private fun fitsE164(e164: String): Boolean = e164.count(::isAsciiDigit) <= MAX_E164_DIGITS

    /**
     * Splits one run into numbers: from each position, take the longest group of
     * space-separated tokens that validates as a national or "+" number; failing that, the
     * longest that reads as a number missing its "+"; failing that, the longest a "+" number
     * allows.
     */
    private fun collectNumbers(run: String, region: String, out: MutableSet<String>) {
        val tokens = run.split(' ').filter { it.isNotEmpty() }
        var start = 0
        while (start < tokens.size) {
            val match = longestNumberAt(tokens, start, region)
            if (match != null) {
                out += match.first
                start = match.second
            } else {
                start++
            }
        }
    }

    /** Returns (E.164, index after the last token used), or null if no number starts at [start]. */
    private fun longestNumberAt(tokens: List<String>, start: Int, region: String): Pair<String, Int>? {
        val candidates = mutableListOf<Pair<String, Int>>()
        var digits = 0
        for (end in start until tokens.size) {
            if (isDate(tokens[end])) break
            digits += tokens[end].count(::isAsciiDigit)
            if (digits > MAX_DIALLED_DIGITS) break
            if (digits >= MIN_EXTRACTED_DIGITS) {
                candidates += tokens.subList(start, end + 1).joinToString(" ") to end + 1
            }
        }
        candidates.asReversed().forEach { (text, end) ->
            strictE164(text, region)?.let { return it to end }
        }
        candidates.asReversed().forEach { (text, end) ->
            bareInternationalE164(text, region)?.let { return it to end }
        }
        candidates.asReversed().forEach { (text, end) ->
            lenientE164(text)?.let { return it to end }
        }
        return null
    }

    private fun isDate(token: String): Boolean {
        val groups = DATE.matchEntire(token)?.groupValues ?: return false
        val (first, second) = if (groups[1].isNotEmpty()) groups[2] to groups[3] else groups[4] to groups[5]
        val a = first.toInt()
        val b = second.toInt()
        return a in 1..31 && b in 1..31 && (a <= 12 || b <= 12)
    }

    private fun isStuckToWord(text: String, range: IntRange): Boolean {
        val before = text.getOrNull(range.first - 1)
        val after = text.getOrNull(range.last + 1)
        return before == '#' || isWordChar(before) || isWordChar(after)
    }

    private fun isWordChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_' || c == '@')

    private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

    private const val NANP_CALLING_CODE = "1"
    private const val FULLWIDTH_PLUS = 0xFF0B
    private const val SMALL_PLUS = 0xFE62
    private const val MINUS_SIGN = 0x2212
}
