package com.harshalbhatia.whatsopen

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.harshalbhatia.whatsopen.WhatsAppLauncher.AppChoice
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class WhatsAppLauncherTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var now = 1_000_000L

    @Before
    fun setUp() {
        WhatsAppLauncher.resetForTesting()
        WhatsAppLauncher.clearRememberedApp(app)
        WhatsAppLauncher.clock = { now }
    }

    @After
    fun tearDown() {
        WhatsAppLauncher.resetForTesting()
        WhatsAppLauncher.clearRememberedApp(app)
    }

    private fun install(app: WhatsAppApp, enabled: Boolean = true) {
        val info = PackageInfo().apply {
            packageName = app.packageName
            applicationInfo = ApplicationInfo().apply {
                packageName = app.packageName
                this.enabled = enabled
            }
        }
        shadowOf(this.app.packageManager).installPackage(info)
    }

    private fun nextStarted(): Intent? = shadowOf(app).nextStartedActivity

    private fun latestToast(): String? = ShadowToast.getTextOfLatestToast()?.toString()

    // --- buildChatUri ---

    @Test
    fun `chat uri has digits without plus`() {
        val uri = WhatsAppLauncher.buildChatUri("+14155552671")
        assertEquals("https://api.whatsapp.com/send?phone=14155552671", uri.toString())
        assertFalse(uri.toString().contains("+"))
        assertFalse(uri.toString().contains("%2B"))
    }

    @Test
    fun `chat uri leaves out empty message`() {
        assertNull(WhatsAppLauncher.buildChatUri("+14155552671", "").getQueryParameter("text"))
        assertNull(WhatsAppLauncher.buildChatUri("+14155552671", null).getQueryParameter("text"))
    }

    @Test
    fun `chat uri encodes message so ampersand and hash stay in text`() {
        val message = "Hi & welcome #1, 100% sure? a=b+c"
        val uri = WhatsAppLauncher.buildChatUri("+919876543210", message)
        val parsed = Uri.parse(uri.toString())
        assertEquals("919876543210", parsed.getQueryParameter("phone"))
        assertEquals(message, parsed.getQueryParameter("text"))
        assertEquals(setOf("phone", "text"), parsed.queryParameterNames)
        assertNull(parsed.fragment)
        assertFalse(uri.toString().contains(" "))
        assertTrue(uri.encodedQuery!!.contains("%26"))
        assertTrue(uri.encodedQuery!!.contains("%23"))
    }

    // --- openChat: validation ---

    @Test
    fun `invalid number shows error and launches nothing`() {
        install(WhatsAppApp.CONSUMER)
        assertEquals(LaunchResult.InvalidNumber, WhatsAppLauncher.openChat(app, "12345", "US"))
        assertEquals(LaunchResult.InvalidNumber, WhatsAppLauncher.openChat(app, "Order #12345678", "US"))
        assertEquals(LaunchResult.InvalidNumber, WhatsAppLauncher.openChat(app, "+1234567890123456", "US"))
        assertNull(nextStarted())
        assertEquals(app.getString(R.string.error_invalid_phone_number), latestToast())
    }

    @Test
    fun `invalid number does not start the debounce window`() {
        install(WhatsAppApp.CONSUMER)
        WhatsAppLauncher.openChat(app, "12345", "US")
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
    }

    @Test
    fun `opens the normalized number`() {
        install(WhatsAppApp.CONSUMER)
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "07911 123456", "GB"))
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("447911123456", intent.data!!.getQueryParameter("phone"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `passes message to the chat`() {
        install(WhatsAppApp.CONSUMER)
        WhatsAppLauncher.openChat(app, "+14155552671", "US", message = "Hello & bye")
        assertEquals("Hello & bye", nextStarted()!!.data!!.getQueryParameter("text"))
    }

    // --- openChat: debounce ---

    @Test
    fun `second launch within 700 ms is ignored`() {
        install(WhatsAppApp.CONSUMER)
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertNotNull(nextStarted())

        now += WhatsAppLauncher.DEBOUNCE_MS - 1
        assertEquals(LaunchResult.Ignored, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertNull(nextStarted())

        now += 1
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertNotNull(nextStarted())
    }

    // --- openChat: package selection ---

    @Test
    fun `not installed shows error`() {
        assertEquals(LaunchResult.NotInstalled, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertNull(nextStarted())
        assertEquals(app.getString(R.string.error_whatsapp_not_installed), latestToast())
    }

    @Test
    fun `only WhatsApp Business installed opens Business`() {
        install(WhatsAppApp.BUSINESS)
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertEquals("com.whatsapp.w4b", nextStarted()!!.`package`)
    }

    @Test
    fun `disabled WhatsApp is skipped for Business`() {
        install(WhatsAppApp.CONSUMER, enabled = false)
        install(WhatsAppApp.BUSINESS)
        assertEquals(listOf(WhatsAppApp.BUSINESS), WhatsAppLauncher.availableApps(app))
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertEquals("com.whatsapp.w4b", nextStarted()!!.`package`)
    }

    @Test
    fun `only disabled app counts as not installed`() {
        install(WhatsAppApp.CONSUMER, enabled = false)
        assertEquals(LaunchResult.NotInstalled, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
    }

    @Test
    fun `both installed without an activity opens WhatsApp`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        assertEquals("com.whatsapp", nextStarted()!!.`package`)
    }

    @Test
    fun `falls back to the other app when the first can't open the chat`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val started = mutableListOf<String?>()
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                started += intent.`package`
                if (intent.`package` == WhatsAppApp.CONSUMER.packageName) throw ActivityNotFoundException()
            }
        }
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(context, "+14155552671", "US"))
        assertEquals(listOf("com.whatsapp", "com.whatsapp.w4b"), started)
    }

    @Test
    fun `reports failure when no app can open the chat`() {
        install(WhatsAppApp.CONSUMER)
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) = throw ActivityNotFoundException()
        }
        assertEquals(LaunchResult.Failed, WhatsAppLauncher.openChat(context, "+14155552671", "US"))
        assertEquals(app.getString(R.string.error_opening_whatsapp), latestToast())
    }

    @Test
    fun `a failed launch can be retried straight away`() {
        install(WhatsAppApp.CONSUMER)
        var fail = true
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                if (fail) throw ActivityNotFoundException()
            }
        }
        assertEquals(LaunchResult.Failed, WhatsAppLauncher.openChat(context, "+14155552671", "US"))
        fail = false
        now += 1
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(context, "+14155552671", "US"))
    }

    @Test
    fun `not installed does not start the debounce window`() {
        assertEquals(LaunchResult.NotInstalled, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
        install(WhatsAppApp.CONSUMER)
        now += 1
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(app, "+14155552671", "US"))
    }

    @Test
    fun `chooseApps covers each case`() {
        val both = listOf(WhatsAppApp.CONSUMER, WhatsAppApp.BUSINESS)
        assertEquals(AppChoice.NoneAvailable, WhatsAppLauncher.chooseApps(emptyList(), WhatsAppApp.BUSINESS, true))
        assertEquals(
            AppChoice.Use(listOf(WhatsAppApp.CONSUMER)),
            WhatsAppLauncher.chooseApps(listOf(WhatsAppApp.CONSUMER), null, true)
        )
        assertEquals(
            AppChoice.Use(listOf(WhatsAppApp.BUSINESS)),
            WhatsAppLauncher.chooseApps(listOf(WhatsAppApp.BUSINESS), null, true)
        )
        assertEquals(
            AppChoice.Use(listOf(WhatsAppApp.BUSINESS, WhatsAppApp.CONSUMER)),
            WhatsAppLauncher.chooseApps(both, WhatsAppApp.BUSINESS, true)
        )
        assertEquals(AppChoice.Ask(both), WhatsAppLauncher.chooseApps(both, null, true))
        assertEquals(AppChoice.Use(both), WhatsAppLauncher.chooseApps(both, null, false))
        // A remembered app that has since been removed is ignored.
        assertEquals(
            AppChoice.Use(listOf(WhatsAppApp.CONSUMER)),
            WhatsAppLauncher.chooseApps(listOf(WhatsAppApp.CONSUMER), WhatsAppApp.BUSINESS, true)
        )
    }

    // --- chooser dialog and remembered choice ---

    @Test
    fun `both installed with an activity asks, remembers the choice, and uses it next time`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(LaunchResult.ChoosingApp, WhatsAppLauncher.openChat(activity, "+14155552671", "US"))
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        val list = dialog.listView
        assertEquals(activity.getString(R.string.whatsapp_app_name), list.adapter.getItem(0).toString())
        assertEquals(activity.getString(R.string.whatsapp_business_app_name), list.adapter.getItem(1).toString())

        dialog.findViewById<CheckBox>(R.id.remember_choice)!!.isChecked = true
        list.performItemClick(list.adapter.getView(1, null, list), 1, 1)
        ShadowLooper.idleMainLooper()

        assertEquals("com.whatsapp.w4b", shadowOf(activity).nextStartedActivity.`package`)
        assertEquals(WhatsAppApp.BUSINESS, WhatsAppLauncher.rememberedApp(activity))

        now += WhatsAppLauncher.DEBOUNCE_MS
        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(activity, "+14155552671", "US"))
        assertEquals("com.whatsapp.w4b", shadowOf(activity).nextStartedActivity.`package`)
    }

    @Test
    fun `choice is not remembered unless the box is checked`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        WhatsAppLauncher.openChat(activity, "+14155552671", "US")
        val list = (ShadowDialog.getLatestDialog() as AlertDialog).listView
        list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
        ShadowLooper.idleMainLooper()

        assertEquals("com.whatsapp", shadowOf(activity).nextStartedActivity.`package`)
        assertNull(WhatsAppLauncher.rememberedApp(activity))
    }

    @Test
    fun `onChooserClosed runs when the chooser is cancelled`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var closed = 0

        WhatsAppLauncher.openChat(activity, "+14155552671", "US", onChooserClosed = { closed++ })
        assertEquals(0, closed)
        (ShadowDialog.getLatestDialog() as AlertDialog).cancel()
        ShadowLooper.idleMainLooper()

        assertEquals(1, closed)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `onChooserClosed runs after the picked app was started`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var startedWhenClosed: Intent? = null

        WhatsAppLauncher.openChat(activity, "+14155552671", "US", onChooserClosed = {
            startedWhenClosed = shadowOf(activity).peekNextStartedActivity()
        })
        val list = (ShadowDialog.getLatestDialog() as AlertDialog).listView
        list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
        ShadowLooper.idleMainLooper()

        assertEquals("com.whatsapp", startedWhenClosed?.`package`)
    }

    @Test
    fun `onChooserClosed does not run when no chooser is shown`() {
        install(WhatsAppApp.CONSUMER)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var closed = false

        assertEquals(
            LaunchResult.Launched,
            WhatsAppLauncher.openChat(activity, "+14155552671", "US", onChooserClosed = { closed = true })
        )
        ShadowLooper.idleMainLooper()

        assertFalse(closed)
    }

    @Test
    fun `clearRememberedApp forgets the choice`() {
        app.getSharedPreferences("whatsapp_launcher", Context.MODE_PRIVATE).edit()
            .putString("remembered_app", WhatsAppApp.BUSINESS.packageName).commit()
        assertEquals(WhatsAppApp.BUSINESS, WhatsAppLauncher.rememberedApp(app))
        assertTrue(WhatsAppLauncher.clearRememberedApp(app))
        assertNull(WhatsAppLauncher.rememberedApp(app))
        assertFalse(WhatsAppLauncher.clearRememberedApp(app))
    }

    @Test
    fun `remembered app is used without asking`() {
        install(WhatsAppApp.CONSUMER)
        install(WhatsAppApp.BUSINESS)
        app.getSharedPreferences("whatsapp_launcher", Context.MODE_PRIVATE).edit()
            .putString("remembered_app", WhatsAppApp.BUSINESS.packageName).commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(LaunchResult.Launched, WhatsAppLauncher.openChat(activity, "+14155552671", "US"))
        assertEquals("com.whatsapp.w4b", shadowOf(activity).nextStartedActivity.`package`)
    }
}
