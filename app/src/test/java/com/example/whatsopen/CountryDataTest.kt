package com.example.whatsopen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryDataTest {

    @Test
    fun `all country codes are unique`() {
        val codes = COUNTRY_DATA.keys.toList()
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `all entries have non-empty flag`() {
        COUNTRY_DATA.forEach { (code, info) ->
            assertTrue("Country code $code has empty flag", info.flag.isNotEmpty())
        }
    }

    @Test
    fun `all entries have non-empty name`() {
        COUNTRY_DATA.forEach { (code, info) ->
            assertTrue("Country code $code has empty name", info.name.isNotEmpty())
        }
    }

    @Test
    fun `lookup US returns correct info`() {
        val info = COUNTRY_DATA["1"]
        assertNotNull(info)
        assertEquals("United States", info!!.name)
    }

    @Test
    fun `lookup India returns correct info`() {
        val info = COUNTRY_DATA["91"]
        assertNotNull(info)
        assertEquals("India", info!!.name)
    }

    @Test
    fun `lookup UK returns correct info`() {
        val info = COUNTRY_DATA["44"]
        assertNotNull(info)
        assertEquals("United Kingdom", info!!.name)
    }

    @Test
    fun `unknown code returns null`() {
        assertNull(COUNTRY_DATA["999"])
    }

    @Test
    fun `country data contains expected number of entries`() {
        assertTrue("Expected at least 60 countries", COUNTRY_DATA.size >= 60)
    }
}
