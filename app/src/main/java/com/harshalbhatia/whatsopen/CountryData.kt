package com.harshalbhatia.whatsopen

import java.util.Locale

/** A region (ISO 3166 code, e.g. "CA") and its international calling code (e.g. "1"). */
data class CountryInfo(val regionCode: String, val callingCode: String) {

    /** Flag emoji made from the region code's regional-indicator letters. */
    val flag: String
        get() = buildString {
            regionCode.forEach { appendCodePoint(REGIONAL_INDICATOR_A + (it - 'A')) }
        }

    /** Region name in the user's current language. */
    val name: String
        get() = displayName(Locale.getDefault())

    /** Region name in [locale]; falls back to the region code if the locale has no name for it. */
    fun displayName(locale: Locale): String =
        Locale("", regionCode).getDisplayCountry(locale).ifBlank { regionCode }

    private companion object {
        const val REGIONAL_INDICATOR_A = 0x1F1E6
    }
}

object CountryData {

    /**
     * Every geographic calling code and the regions that use it, main region first
     * (the one libphonenumber formats the code for). Taken from the libphonenumber metadata
     * shipped with Android 15: 245 regions, 206 codes. Non-geographic codes (+800, +882, …)
     * aren't regions and are left out.
     */
    private val REGIONS_BY_CALLING_CODE: Map<String, List<String>> = mapOf(
        "1" to listOf(
            "US", "AG", "AI", "AS", "BB", "BM", "BS", "CA", "DM", "DO", "GD", "GU", "JM",
            "KN", "KY", "LC", "MP", "MS", "PR", "SX", "TC", "TT", "VC", "VG", "VI",
        ),
        "7" to listOf("RU", "KZ"),
        "20" to listOf("EG"),
        "27" to listOf("ZA"),
        "30" to listOf("GR"),
        "31" to listOf("NL"),
        "32" to listOf("BE"),
        "33" to listOf("FR"),
        "34" to listOf("ES"),
        "36" to listOf("HU"),
        "39" to listOf("IT", "VA"),
        "40" to listOf("RO"),
        "41" to listOf("CH"),
        "43" to listOf("AT"),
        "44" to listOf("GB", "GG", "IM", "JE"),
        "45" to listOf("DK"),
        "46" to listOf("SE"),
        "47" to listOf("NO", "SJ"),
        "48" to listOf("PL"),
        "49" to listOf("DE"),
        "51" to listOf("PE"),
        "52" to listOf("MX"),
        "53" to listOf("CU"),
        "54" to listOf("AR"),
        "55" to listOf("BR"),
        "56" to listOf("CL"),
        "57" to listOf("CO"),
        "58" to listOf("VE"),
        "60" to listOf("MY"),
        "61" to listOf("AU", "CC", "CX"),
        "62" to listOf("ID"),
        "63" to listOf("PH"),
        "64" to listOf("NZ"),
        "65" to listOf("SG"),
        "66" to listOf("TH"),
        "81" to listOf("JP"),
        "82" to listOf("KR"),
        "84" to listOf("VN"),
        "86" to listOf("CN"),
        "90" to listOf("TR"),
        "91" to listOf("IN"),
        "92" to listOf("PK"),
        "93" to listOf("AF"),
        "94" to listOf("LK"),
        "95" to listOf("MM"),
        "98" to listOf("IR"),
        "211" to listOf("SS"),
        "212" to listOf("MA", "EH"),
        "213" to listOf("DZ"),
        "216" to listOf("TN"),
        "218" to listOf("LY"),
        "220" to listOf("GM"),
        "221" to listOf("SN"),
        "222" to listOf("MR"),
        "223" to listOf("ML"),
        "224" to listOf("GN"),
        "225" to listOf("CI"),
        "226" to listOf("BF"),
        "227" to listOf("NE"),
        "228" to listOf("TG"),
        "229" to listOf("BJ"),
        "230" to listOf("MU"),
        "231" to listOf("LR"),
        "232" to listOf("SL"),
        "233" to listOf("GH"),
        "234" to listOf("NG"),
        "235" to listOf("TD"),
        "236" to listOf("CF"),
        "237" to listOf("CM"),
        "238" to listOf("CV"),
        "239" to listOf("ST"),
        "240" to listOf("GQ"),
        "241" to listOf("GA"),
        "242" to listOf("CG"),
        "243" to listOf("CD"),
        "244" to listOf("AO"),
        "245" to listOf("GW"),
        "246" to listOf("IO"),
        "247" to listOf("AC"),
        "248" to listOf("SC"),
        "249" to listOf("SD"),
        "250" to listOf("RW"),
        "251" to listOf("ET"),
        "252" to listOf("SO"),
        "253" to listOf("DJ"),
        "254" to listOf("KE"),
        "255" to listOf("TZ"),
        "256" to listOf("UG"),
        "257" to listOf("BI"),
        "258" to listOf("MZ"),
        "260" to listOf("ZM"),
        "261" to listOf("MG"),
        "262" to listOf("RE", "YT"),
        "263" to listOf("ZW"),
        "264" to listOf("NA"),
        "265" to listOf("MW"),
        "266" to listOf("LS"),
        "267" to listOf("BW"),
        "268" to listOf("SZ"),
        "269" to listOf("KM"),
        "290" to listOf("SH", "TA"),
        "291" to listOf("ER"),
        "297" to listOf("AW"),
        "298" to listOf("FO"),
        "299" to listOf("GL"),
        "350" to listOf("GI"),
        "351" to listOf("PT"),
        "352" to listOf("LU"),
        "353" to listOf("IE"),
        "354" to listOf("IS"),
        "355" to listOf("AL"),
        "356" to listOf("MT"),
        "357" to listOf("CY"),
        "358" to listOf("FI", "AX"),
        "359" to listOf("BG"),
        "370" to listOf("LT"),
        "371" to listOf("LV"),
        "372" to listOf("EE"),
        "373" to listOf("MD"),
        "374" to listOf("AM"),
        "375" to listOf("BY"),
        "376" to listOf("AD"),
        "377" to listOf("MC"),
        "378" to listOf("SM"),
        "380" to listOf("UA"),
        "381" to listOf("RS"),
        "382" to listOf("ME"),
        "383" to listOf("XK"),
        "385" to listOf("HR"),
        "386" to listOf("SI"),
        "387" to listOf("BA"),
        "389" to listOf("MK"),
        "420" to listOf("CZ"),
        "421" to listOf("SK"),
        "423" to listOf("LI"),
        "500" to listOf("FK"),
        "501" to listOf("BZ"),
        "502" to listOf("GT"),
        "503" to listOf("SV"),
        "504" to listOf("HN"),
        "505" to listOf("NI"),
        "506" to listOf("CR"),
        "507" to listOf("PA"),
        "508" to listOf("PM"),
        "509" to listOf("HT"),
        "590" to listOf("GP", "BL", "MF"),
        "591" to listOf("BO"),
        "592" to listOf("GY"),
        "593" to listOf("EC"),
        "594" to listOf("GF"),
        "595" to listOf("PY"),
        "596" to listOf("MQ"),
        "597" to listOf("SR"),
        "598" to listOf("UY"),
        "599" to listOf("CW", "BQ"),
        "670" to listOf("TL"),
        "672" to listOf("NF"),
        "673" to listOf("BN"),
        "674" to listOf("NR"),
        "675" to listOf("PG"),
        "676" to listOf("TO"),
        "677" to listOf("SB"),
        "678" to listOf("VU"),
        "679" to listOf("FJ"),
        "680" to listOf("PW"),
        "681" to listOf("WF"),
        "682" to listOf("CK"),
        "683" to listOf("NU"),
        "685" to listOf("WS"),
        "686" to listOf("KI"),
        "687" to listOf("NC"),
        "688" to listOf("TV"),
        "689" to listOf("PF"),
        "690" to listOf("TK"),
        "691" to listOf("FM"),
        "692" to listOf("MH"),
        "850" to listOf("KP"),
        "852" to listOf("HK"),
        "853" to listOf("MO"),
        "855" to listOf("KH"),
        "856" to listOf("LA"),
        "880" to listOf("BD"),
        "886" to listOf("TW"),
        "960" to listOf("MV"),
        "961" to listOf("LB"),
        "962" to listOf("JO"),
        "963" to listOf("SY"),
        "964" to listOf("IQ"),
        "965" to listOf("KW"),
        "966" to listOf("SA"),
        "967" to listOf("YE"),
        "968" to listOf("OM"),
        "970" to listOf("PS"),
        "971" to listOf("AE"),
        "972" to listOf("IL"),
        "973" to listOf("BH"),
        "974" to listOf("QA"),
        "975" to listOf("BT"),
        "976" to listOf("MN"),
        "977" to listOf("NP"),
        "992" to listOf("TJ"),
        "993" to listOf("TM"),
        "994" to listOf("AZ"),
        "995" to listOf("GE"),
        "996" to listOf("KG"),
        "998" to listOf("UZ"),
    )

    /** Longest calling code, in digits. */
    private const val MAX_CALLING_CODE_LENGTH = 3

    /** Every region, once, grouped by calling code. */
    val all: List<CountryInfo> = REGIONS_BY_CALLING_CODE.flatMap { (code, regions) ->
        regions.map { CountryInfo(it, code) }
    }

    private val byRegion: Map<String, CountryInfo> = all.associateBy { it.regionCode }

    /**
     * The region to show for a calling code ("1", "44"): [userRegion] if it uses that code
     * (Canada for +1 on a Canadian phone), otherwise the code's main region (United States).
     * Null if the code isn't assigned to any region.
     */
    fun forCallingCode(callingCode: String, userRegion: String? = null): CountryInfo? {
        val regions = REGIONS_BY_CALLING_CODE[callingCode] ?: return null
        val user = DefaultRegion.cleanIso(userRegion)
        val region = if (user != null && user in regions) user else regions.first()
        return byRegion[region]
    }

    /** The entry for an ISO 3166 region code (any case), or null if it has no calling code. */
    fun forRegion(regionCode: String): CountryInfo? =
        DefaultRegion.cleanIso(regionCode)?.let { byRegion[it] }

    /**
     * The calling code [digits] starts with ("14155552671" gives "1"), or null.
     * Calling codes are prefix-free, so at most one can match.
     */
    fun callingCodePrefix(digits: String): String? =
        (1..minOf(MAX_CALLING_CODE_LENGTH, digits.length))
            .map { digits.substring(0, it) }
            .firstOrNull { it in REGIONS_BY_CALLING_CODE }
}
