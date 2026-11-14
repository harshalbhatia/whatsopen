package com.harshalbhatia.whatsopen

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.BadParcelableException
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class OpenChatActivityTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        WhatsAppLauncher.resetForTesting()
        WhatsAppLauncher.clearRememberedApp(app)
        PendingShare.text = null
        setClipboard("")
    }

    @After
    fun tearDown() {
        WhatsAppLauncher.resetForTesting()
        WhatsAppLauncher.clearRememberedApp(app)
        PendingShare.text = null
        setClipboard("")
    }

    // --- helpers ---

    private fun install(whatsApp: WhatsAppApp) {
        val info = PackageInfo().apply {
            packageName = whatsApp.packageName
            applicationInfo = ApplicationInfo().apply {
                packageName = whatsApp.packageName
                enabled = true
            }
        }
        shadowOf(app.packageManager).installPackage(info)
    }

    private fun setClipboard(text: String) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("", text))
    }

    private fun launch(intent: Intent): OpenChatActivity =
        Robolectric.buildActivity(OpenChatActivity::class.java, intent).setup().get()

    /** Paste & Open, started the way the shortcut and tile start it. Not focused yet. */
    private fun buildPasteAndOpen(): ActivityController<OpenChatActivity> =
        Robolectric.buildActivity(OpenChatActivity::class.java, OpenChatActivity.pasteAndOpenIntent(app)).setup()

    private fun tel(action: String, number: String) = Intent(action, Uri.parse("tel:$number"))

    private fun send(text: String) =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun processText(text: String) =
        Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, text)

    private fun nextStarted(): Intent? = shadowOf(app).nextStartedActivity

    private fun latestToast(): String? = ShadowToast.getTextOfLatestToast()?.toString()

    private fun assertOpenedChat(started: Intent?, phone: String, packageName: String = "com.whatsapp") {
        assertNotNull("no activity started", started)
        assertEquals(Intent.ACTION_VIEW, started!!.action)
        assertEquals(packageName, started.`package`)
        assertEquals(phone, started.data!!.getQueryParameter("phone"))
        // Not in this activity's task, which is hidden from Recents.
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private fun assertOpenedClipboardTab(started: Intent?, text: String) {
        assertNotNull("no activity started", started)
        assertEquals(ComponentName(app, MainActivity::class.java), started!!.component)
        assertEquals(MainActivity.TAB_CLIPBOARD, started.getStringExtra(MainActivity.EXTRA_OPEN_TAB))
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(flags, started.flags and flags)
        assertEquals(text, PendingShare.text)
    }

    // --- tel: links ---

    @Test
    fun `tel VIEW with a valid number opens the chat and finishes`() {
        install(WhatsAppApp.CONSUMER)
        val activity = launch(tel(Intent.ACTION_VIEW, "+14155552671"))
        assertOpenedChat(nextStarted(), "14155552671")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `tel DIAL with a valid number opens the chat and finishes`() {
        install(WhatsAppApp.CONSUMER)
        val activity = launch(tel(Intent.ACTION_DIAL, "+919876543210"))
        assertOpenedChat(nextStarted(), "919876543210")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `tel link without a valid number shows a toast and finishes`() {
        install(WhatsAppApp.CONSUMER)
        val activity = launch(tel(Intent.ACTION_VIEW, "12"))
        assertEquals(app.getString(R.string.error_link_without_number), latestToast())
        assertNull(nextStarted())
        assertTrue(activity.isFinishing)
        assertNull(PendingShare.text)
    }

    @Test
    fun `WhatsApp not installed shows its toast and finishes`() {
        val activity = launch(tel(Intent.ACTION_VIEW, "+14155552671"))
        assertEquals(app.getString(R.string.error_whatsapp_not_installed), latestToast())
        assertNull(nextStarted())
        assertTrue(activity.isFinishing)
    }

    // --- shared and selected text ---

    @Test
    fun `SEND with one number opens the chat`() {
        install(WhatsAppApp.CONSUMER)
        val activity = launch(send("Call me at +14155552671 tomorrow"))
        assertOpenedChat(nextStarted(), "14155552671")
        assertTrue(activity.isFinishing)
        assertNull(PendingShare.text)
    }

    @Test
    fun `SEND with two numbers opens the Clipboard tab with the text`() {
        install(WhatsAppApp.CONSUMER)
        val text = "Office +14155552671, home +442079460958"
        val activity = launch(send(text))
        assertOpenedClipboardTab(nextStarted(), text)
        assertNull(nextStarted())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `SEND without a number opens the Clipboard tab with the text`() {
        val activity = launch(send("hello world no numbers here"))
        assertOpenedClipboardTab(nextStarted(), "hello world no numbers here")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `PROCESS_TEXT with one number opens the chat`() {
        install(WhatsAppApp.CONSUMER)
        val activity = launch(processText("+14155552671"))
        assertOpenedChat(nextStarted(), "14155552671")
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `PROCESS_TEXT with two numbers opens the Clipboard tab`() {
        val text = "+14155552671 or +442079460958"
        val activity = launch(processText(text))
        assertOpenedClipboardTab(nextStarted(), text)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `SEND without text finishes quietly`() {
        val activity = launch(Intent(Intent.ACTION_SEND).setType("text/plain"))
        assertNull(nextStarted())
        assertNull(latestToast())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `SEND with blank text finishes quietly`() {
        val activity = launch(send("   "))
        assertNull(nextStarted())
        assertNull(PendingShare.text)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `extras that fail to unparcel finish quietly`() {
        install(WhatsAppApp.CONSUMER)
        val intent = object : Intent(ACTION_SEND) {
            override fun getCharSequenceExtra(name: String?): CharSequence = throw BadParcelableException("bad")
            override fun getStringExtra(name: String?): String = throw BadParcelableException("bad")
        }
        val activity = launch(intent)
        assertNull(nextStarted())
        assertNull(PendingShare.text)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `unknown or missing action finishes quietly`() {
        assertTrue(launch(Intent()).isFinishing)
        assertTrue(launch(Intent("com.example.UNKNOWN")).isFinishing)
        assertNull(nextStarted())
        assertNull(latestToast())
    }

    @Test
    fun `launch from Recents does nothing`() {
        install(WhatsAppApp.CONSUMER)
        val intent = tel(Intent.ACTION_VIEW, "+14155552671").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val activity = launch(intent)
        assertNull(nextStarted())
        assertTrue(activity.isFinishing)
    }

    // --- WhatsApp chooser ---

    @Test
    fun `with both apps it waits for the chooser and finishes when it is dismissed`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val activity = launch(send("+14155552671"))

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertFalse(activity.isFinishing)

        dialog.cancel()
        ShadowLooper.idleMainLooper()

        assertNull(nextStarted())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `with both apps picking one opens the chat and finishes`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val activity = launch(tel(Intent.ACTION_VIEW, "+14155552671"))

        val list = (ShadowDialog.getLatestDialog() as AlertDialog).listView
        list.performItemClick(list.adapter.getView(1, null, list), 1, 1)
        ShadowLooper.idleMainLooper()

        assertOpenedChat(nextStarted(), "14155552671", "com.whatsapp.w4b")
        assertTrue(activity.isFinishing)
    }

    // --- Paste & Open ---

    @Test
    fun `Paste & Open does not read the clipboard before it has window focus`() {
        install(WhatsAppApp.CONSUMER)
        setClipboard("+14155552671")
        val activity = buildPasteAndOpen().get()
        assertNull(nextStarted())
        assertNull(latestToast())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `Paste & Open with one number on the clipboard opens the chat once focused`() {
        install(WhatsAppApp.CONSUMER)
        setClipboard("Number: +14155552671")
        val controller = buildPasteAndOpen()

        controller.windowFocusChanged(true)

        assertOpenedChat(nextStarted(), "14155552671")
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `Paste & Open with an empty clipboard shows a toast and finishes`() {
        install(WhatsAppApp.CONSUMER)
        val controller = buildPasteAndOpen()

        controller.windowFocusChanged(true)

        assertEquals(app.getString(R.string.error_clipboard_empty), latestToast())
        assertNull(nextStarted())
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `Paste & Open with no clip at all shows a toast`() {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        val controller = buildPasteAndOpen()

        controller.windowFocusChanged(true)

        assertEquals(app.getString(R.string.error_clipboard_empty), latestToast())
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `Paste & Open with several numbers opens the Clipboard tab`() {
        install(WhatsAppApp.CONSUMER)
        val text = "+14155552671 and +442079460958"
        setClipboard(text)
        val controller = buildPasteAndOpen()

        controller.windowFocusChanged(true)

        assertOpenedClipboardTab(nextStarted(), text)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `Paste & Open reads the clipboard only once`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        setClipboard("+14155552671")
        val controller = buildPasteAndOpen()

        controller.windowFocusChanged(true)
        val chooser = ShadowDialog.getLatestDialog()
        assertTrue(chooser.isShowing)

        // Focus moves to the chooser and back.
        controller.windowFocusChanged(false)
        controller.windowFocusChanged(true)

        assertTrue(chooser === ShadowDialog.getLatestDialog())
        assertNull(nextStarted())
        assertFalse(controller.get().isFinishing)
    }

    @Test
    fun `the exported activity ignores the Paste & Open action`() {
        install(WhatsAppApp.CONSUMER)
        setClipboard("+14155552671")
        val intent = Intent(OpenChatActivity.ACTION_PASTE_AND_OPEN).setClass(app, OpenChatActivity::class.java)
        val controller = Robolectric.buildActivity(OpenChatActivity::class.java, intent).setup()

        controller.windowFocusChanged(true)

        assertNull(nextStarted())
        assertTrue(controller.get().isFinishing)
    }

    // --- manifest ---

    @Test
    fun `shares, tel links and selected text go to this activity, not MainActivity`() {
        val pm = app.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_SEND).setType("text/plain"),
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"),
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:+14155552671")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:+14155552671")),
        )
        for (intent in intents) {
            val targets = pm.queryIntentActivities(intent.setPackage(app.packageName), 0).map { it.activityInfo.name }
            assertEquals(intent.action, listOf(OpenChatActivity::class.java.name), targets)
        }
    }

    @Test
    fun `Paste & Open alias is not exported and the share entry is`() {
        val pm = app.packageManager
        val alias = pm.getActivityInfo(ComponentName(app, OpenChatActivity.PASTE_AND_OPEN_ALIAS), 0)
        assertFalse(alias.exported)
        assertEquals(OpenChatActivity::class.java.name, alias.targetActivity)

        val trampoline = pm.getActivityInfo(ComponentName(app, OpenChatActivity::class.java), 0)
        assertTrue(trampoline.exported)
        assertTrue(trampoline.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        assertTrue(trampoline.flags and ActivityInfo.FLAG_NO_HISTORY != 0)
        assertTrue(trampoline.taskAffinity.isNullOrEmpty())

        val main = pm.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertTrue(main.taskAffinity.isNullOrEmpty())
    }

    @Test
    fun `Paste & Open intent targets the alias`() {
        val intent = OpenChatActivity.pasteAndOpenIntent(app)
        assertEquals(ComponentName(app, OpenChatActivity.PASTE_AND_OPEN_ALIAS), intent.component)
        assertEquals(OpenChatActivity.ACTION_PASTE_AND_OPEN, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
