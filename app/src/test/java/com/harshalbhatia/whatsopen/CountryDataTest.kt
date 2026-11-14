package com.harshalbhatia.whatsopen

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CountryDataTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `each region appears once`() {
        val regions = CountryData.all.map { it.regionCode }
        assertEquals(regions.size, regions.distinct().size)
    }

    @Test
    fun `covers every calling region`() {
        assertEquals(245, CountryData.all.size)
        assertEquals(206, CountryData.all.map { it.callingCode }.distinct().size)
    }

    @Test
    fun `region codes are two uppercase letters and calling codes are 1 to 3 digits`() {
        CountryData.all.forEach {
            assertTrue(it.regionCode, it.regionCode.matches(Regex("[A-Z]{2}")))
            assertTrue(it.callingCode, it.callingCode.matches(Regex("[1-9][0-9]{0,2}")))
        }
    }

    @Test
    fun `calling codes are prefix-free`() {
        val codes = CountryData.all.map { it.callingCode }.distinct()
        for (a in codes) for (b in codes) {
            if (a != b) assertTrue("$a is a prefix of $b", !b.startsWith(a))
        }
    }

    @Test
    fun `flag is built from regional indicator letters`() {
        assertEquals("🇮🇳", CountryData.forRegion("IN")!!.flag)
        assertEquals("🇬🇧", CountryData.forRegion("gb")!!.flag)
        CountryData.all.forEach {
            assertEquals(it.regionCode, 4, it.flag.length)
        }
    }

    @Test
    fun `every region has a display name`() {
        CountryData.all.forEach {
            assertTrue("${it.regionCode} has no name", it.name.isNotBlank())
            assertNotEquals("${it.regionCode} has no localized name", it.regionCode, it.name)
        }
    }

    @Test
    fun `names follow the default locale`() {
        val germany = CountryData.forRegion("DE")!!
        Locale.setDefault(Locale.GERMANY)
        assertEquals("Deutschland", germany.name)
        Locale.setDefault(Locale.FRANCE)
        assertEquals("Allemagne", germany.name)
    }

    @Test
    fun `calling code lookup returns main region by default`() {
        assertEquals("US", CountryData.forCallingCode("1")!!.regionCode)
        assertEquals("RU", CountryData.forCallingCode("7")!!.regionCode)
        assertEquals("GB", CountryData.forCallingCode("44")!!.regionCode)
        assertEquals("IT", CountryData.forCallingCode("39")!!.regionCode)
        assertEquals("NO", CountryData.forCallingCode("47")!!.regionCode)
        assertEquals("AU", CountryData.forCallingCode("61")!!.regionCode)
        assertEquals("RE", CountryData.forCallingCode("262")!!.regionCode)
        assertEquals("GP", CountryData.forCallingCode("590")!!.regionCode)
        assertEquals("CW", CountryData.forCallingCode("599")!!.regionCode)
        assertEquals("MA", CountryData.forCallingCode("212")!!.regionCode)
        assertEquals("IN", CountryData.forCallingCode("91")!!.regionCode)
    }

    @Test
    fun `calling code lookup prefers the user's region when it shares the code`() {
        assertEquals("CA", CountryData.forCallingCode("1", "CA")!!.regionCode)
        assertEquals("JM", CountryData.forCallingCode("1", "jm")!!.regionCode)
        assertEquals("KZ", CountryData.forCallingCode("7", "KZ")!!.regionCode)
        assertEquals("JE", CountryData.forCallingCode("44", "JE")!!.regionCode)
        assertEquals("YT", CountryData.forCallingCode("262", "YT")!!.regionCode)
    }

    @Test
    fun `calling code lookup ignores user region that uses another code`() {
        assertEquals("US", CountryData.forCallingCode("1", "IN")!!.regionCode)
        assertEquals("1", CountryData.forCallingCode("1", "IN")!!.callingCode)
    }

    @Test
    fun `unknown calling code returns null`() {
        assertNull(CountryData.forCallingCode("999"))
        assertNull(CountryData.forCallingCode(""))
        assertNull(CountryData.forRegion("ZZ"))
    }

    @Test
    fun `calling code prefix finds the code at the start`() {
        assertEquals("1", CountryData.callingCodePrefix("14155552671"))
        assertEquals("91", CountryData.callingCodePrefix("919876543210"))
        assertEquals("212", CountryData.callingCodePrefix("2121234567"))
        assertNull(CountryData.callingCodePrefix("9991234567"))
        assertNull(CountryData.callingCodePrefix(""))
    }
}
