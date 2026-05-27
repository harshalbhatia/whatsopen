package com.harshalbhatia.whatsopen

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import com.harshalbhatia.whatsopen.databinding.FragmentFirstBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirstFragmentTest {

    @Test
    fun `country code input layout shows plus prefix`() {
        val scenario = launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentFirstBinding.bind(fragment.requireView())
            assertEquals("+", binding.countryCodeInputLayout.prefixText.toString())
        }
    }

    @Test
    fun `typing a known country code populates country info`() {
        val scenario = launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentFirstBinding.bind(fragment.requireView())
            binding.countryCodeInput.setText("91")
            val info = binding.countryInfo.text.toString()
            assertNotNull(info)
            assert(info.contains("India")) { "expected India in country info, got $info" }
        }
    }

    @Test
    fun `typing an unknown country code clears country info`() {
        val scenario = launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentFirstBinding.bind(fragment.requireView())
            binding.countryCodeInput.setText("999")
            assertEquals("", binding.countryInfo.text.toString())
        }
    }

    @Test
    fun `submit with empty country code shows error`() {
        val scenario = launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentFirstBinding.bind(fragment.requireView())
            binding.phoneInput.setText("9876543210")
            binding.openWhatsappButton.performClick()
            assertNotNull(binding.countryCodeInputLayout.error)
        }
    }

    @Test
    fun `submit with empty phone shows error`() {
        val scenario = launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentFirstBinding.bind(fragment.requireView())
            binding.countryCodeInput.setText("91")
            binding.openWhatsappButton.performClick()
            assertNotNull(binding.phoneInputLayout.error)
        }
    }

    @Test
    fun `editing country code clears previous error`() {
        val scenario = launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentFirstBinding.bind(fragment.requireView())
            binding.openWhatsappButton.performClick()
            assertNotNull(binding.countryCodeInputLayout.error)
            binding.countryCodeInput.setText("1")
            assertNull(binding.countryCodeInputLayout.error)
        }
    }
}
