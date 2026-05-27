package com.example.whatsopen.ui.bynumber

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.whatsopen.CountryInfo
import com.example.whatsopen.CountryLookup
import com.example.whatsopen.MainDispatcherRule
import com.example.whatsopen.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ByNumberViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val fakeLookup = object : CountryLookup {
        override fun lookup(numericCode: String): CountryInfo? = when (numericCode) {
            "91" -> CountryInfo("🇮🇳", "India")
            "1" -> CountryInfo("🇺🇸", "United States")
            else -> null
        }
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        ByNumberViewModel(handle, fakeLookup)

    @Test
    fun init_emits_empty_state() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("", s.countryCode)
            assertEquals("", s.phoneNumber)
            assertNull(s.countryName)
            assertNull(s.countryFlag)
            assertNull(s.countryCodeError)
            assertNull(s.phoneError)
        }
    }

    @Test
    fun onCountryCodeChanged_strips_leading_plus_and_non_digits() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem() // initial
            vm.onCountryCodeChanged("+1A2B3")
            val s = awaitItem()
            assertEquals("123", s.countryCode)
        }
    }

    @Test
    fun onCountryCodeChanged_with_known_code_emits_country_flag_and_name() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem()
            vm.onCountryCodeChanged("91")
            val s = awaitItem()
            assertEquals("91", s.countryCode)
            assertEquals("India", s.countryName)
            assertEquals("🇮🇳", s.countryFlag)
        }
    }

    @Test
    fun onCountryCodeChanged_with_unknown_code_clears_country() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem()
            vm.onCountryCodeChanged("999")
            val s = awaitItem()
            assertEquals("999", s.countryCode)
            assertNull(s.countryName)
            assertNull(s.countryFlag)
        }
    }

    @Test
    fun onPhoneChanged_strips_non_digits() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem()
            vm.onPhoneChanged("98-76 543abc210")
            val s = awaitItem()
            assertEquals("9876543210", s.phoneNumber)
        }
    }

    @Test
    fun onSubmit_with_empty_country_code_emits_countryCodeError() = runTest {
        val vm = newViewModel()
        vm.onPhoneChanged("9876543210")
        vm.uiState.test {
            // drain whatever state we have
            val before = awaitItem()
            assertNull(before.countryCodeError)
            vm.onSubmit()
            val s = awaitItem()
            assertEquals(R.string.error_country_code_required, s.countryCodeError)
        }
    }

    @Test
    fun onSubmit_with_empty_phone_emits_phoneError() = runTest {
        val vm = newViewModel()
        vm.onCountryCodeChanged("91")
        vm.uiState.test {
            val before = awaitItem()
            assertNull(before.phoneError)
            vm.onSubmit()
            val s = awaitItem()
            assertEquals(R.string.error_phone_required, s.phoneError)
        }
    }

    @Test
    fun onSubmit_with_valid_input_emits_e164_string() = runTest {
        val vm = newViewModel()
        vm.onCountryCodeChanged("91")
        vm.onPhoneChanged("9876543210")
        vm.submitEvents.test {
            vm.onSubmit()
            assertEquals("919876543210", awaitItem())
        }
    }

    @Test
    fun onCountryCodeChanged_clears_previous_countryCodeError() = runTest {
        val vm = newViewModel()
        vm.onSubmit() // produces error since fields empty
        vm.uiState.test {
            val withError = awaitItem()
            assertEquals(R.string.error_country_code_required, withError.countryCodeError)
            vm.onCountryCodeChanged("91")
            val cleared = expectMostRecentItem()
            assertNull(cleared.countryCodeError)
        }
    }

    @Test
    fun state_survives_process_death() = runTest {
        val handle = SavedStateHandle()
        val vmA = ByNumberViewModel(handle, fakeLookup)
        vmA.onCountryCodeChanged("91")
        vmA.onPhoneChanged("9876543210")

        val vmB = ByNumberViewModel(handle, fakeLookup)
        vmB.uiState.test {
            val s = awaitItem()
            assertEquals("91", s.countryCode)
            assertEquals("9876543210", s.phoneNumber)
            assertEquals("India", s.countryName)
        }
    }
}
