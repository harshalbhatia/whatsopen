package com.example.whatsopen.ui.clipboard

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
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
class ClipboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        ClipboardViewModel(handle)

    @Test
    fun init_text_is_empty() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("", s.text)
            assertNull(s.errorRes)
        }
    }

    @Test
    fun onTextChanged_updates_text() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem() // initial
            vm.onTextChanged("hello")
            val s = awaitItem()
            assertEquals("hello", s.text)
        }
    }

    @Test
    fun onSubmit_empty_text_emits_error() = runTest {
        val vm = newViewModel()
        vm.submitEvents.test {
            vm.onSubmit()
            expectNoEvents()
        }
        vm.uiState.test {
            val s = awaitItem()
            assertEquals(R.string.error_no_valid_numbers, s.errorRes)
        }
    }

    @Test
    fun onSubmit_whitespace_only_emits_error() = runTest {
        val vm = newViewModel()
        vm.onTextChanged("   ")
        vm.submitEvents.test {
            vm.onSubmit()
            expectNoEvents()
        }
        vm.uiState.test {
            val s = awaitItem()
            assertEquals(R.string.error_no_valid_numbers, s.errorRes)
        }
    }

    @Test
    fun onSubmit_no_valid_numbers_emits_error() = runTest {
        val vm = newViewModel()
        vm.onTextChanged("abc xyz no numbers")
        vm.submitEvents.test {
            vm.onSubmit()
            expectNoEvents()
        }
        vm.uiState.test {
            val s = awaitItem()
            assertEquals(R.string.error_no_valid_numbers, s.errorRes)
        }
    }

    @Test
    fun onSubmit_with_valid_numbers_emits_first() = runTest {
        val vm = newViewModel()
        vm.onTextChanged("+14155551234 someone else")
        vm.submitEvents.test {
            vm.onSubmit()
            assertEquals("+14155551234", awaitItem())
        }
    }

    @Test
    fun onTextChanged_clears_existing_error() = runTest {
        val vm = newViewModel()
        vm.onSubmit() // sets error
        vm.uiState.test {
            val withError = awaitItem()
            assertEquals(R.string.error_no_valid_numbers, withError.errorRes)
            vm.onTextChanged("anything")
            val cleared = expectMostRecentItem()
            assertNull(cleared.errorRes)
        }
    }

    @Test
    fun text_survives_process_death() = runTest {
        val handle = SavedStateHandle(mapOf("clipboard_text" to "persisted"))
        val vm = ClipboardViewModel(handle)
        vm.uiState.test {
            val s = awaitItem()
            assertEquals("persisted", s.text)
        }
    }
}
