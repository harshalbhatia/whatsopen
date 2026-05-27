package com.example.whatsopen

interface CountryLookup {
    fun lookup(numericCode: String): CountryInfo?
}

object DefaultCountryLookup : CountryLookup {
    override fun lookup(numericCode: String): CountryInfo? = COUNTRY_DATA[numericCode]
}
