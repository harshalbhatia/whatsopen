package com.harshalbhatia.whatsopen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.harshalbhatia.whatsopen.databinding.FragmentClipboardBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardFragmentTest {

    @After
    fun tearDown() {
        PendingShare.text = null
        clearClipboard()
    }

    private fun clearClipboard() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    private fun setClipboard(text: String) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("label", text))
    }

    @Test
    fun `prefills from PendingShare on resume`() {
        PendingShare.text = "+14155551234"
        val scenario = launchFragmentInContainer<ClipboardFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentClipboardBinding.bind(fragment.requireView())
            assertEquals("+14155551234", binding.numbersInput.text.toString())
        }
    }

    @Test
    fun `consumes PendingShare after prefill`() {
        PendingShare.text = "+14155551234"
        val scenario = launchFragmentInContainer<ClipboardFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment {
            assertEquals(null, PendingShare.text)
        }
    }

    @Test
    fun `prefills from clipboard when PendingShare empty and clipboard has phone number`() {
        setClipboard("+919876543210")
        val scenario = launchFragmentInContainer<ClipboardFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentClipboardBinding.bind(fragment.requireView())
            assertEquals("+919876543210", binding.numbersInput.text.toString())
        }
    }

    @Test
    fun `does not prefill when clipboard has non-number text`() {
        setClipboard("hello world")
        val scenario = launchFragmentInContainer<ClipboardFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentClipboardBinding.bind(fragment.requireView())
            assertEquals("", binding.numbersInput.text.toString())
        }
    }

    @Test
    fun `PendingShare takes precedence over clipboard`() {
        PendingShare.text = "+11111111111"
        setClipboard("+22222222222")
        val scenario = launchFragmentInContainer<ClipboardFragment>(themeResId = R.style.Theme_WhatsOpen)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            val binding = FragmentClipboardBinding.bind(fragment.requireView())
            assertEquals("+11111111111", binding.numbersInput.text.toString())
        }
    }
}
