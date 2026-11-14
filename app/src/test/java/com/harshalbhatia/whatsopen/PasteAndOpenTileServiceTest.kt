package com.harshalbhatia.whatsopen

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class PasteAndOpenTileServiceTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun createService(): PasteAndOpenTileService =
        Robolectric.buildService(PasteAndOpenTileService::class.java).create().get()

    /**
     * Records the PendingIntents passed to startActivityAndCollapse(PendingIntent). Robolectric
     * doesn't shadow that method, which hands them to System UI through the hidden IQSService.
     */
    private fun recordStartedPendingIntents(service: TileService): List<PendingIntent> {
        val started = mutableListOf<PendingIntent>()
        val iqsService = Class.forName("android.service.quicksettings.IQSService")
        val fake = Proxy.newProxyInstance(iqsService.classLoader, arrayOf(iqsService)) { _, method, args ->
            if (method.name == "startActivity") started += args[1] as PendingIntent
            null
        }
        ReflectionHelpers.setField(service, "mService", fake)
        return started
    }

    private fun assertOpensPasteAndOpen(pendingIntent: PendingIntent) {
        val shadow = shadowOf(pendingIntent)
        assertTrue(shadow.isActivity)
        assertTrue(shadow.isImmutable)
        assertEquals(ComponentName(app, OpenChatActivity.PASTE_AND_OPEN_ALIAS), shadow.savedIntent.component)
        assertEquals(OpenChatActivity.ACTION_PASTE_AND_OPEN, shadow.savedIntent.action)
    }

    @Test
    fun `click opens Paste & Open`() {
        val service = createService()
        val started = recordStartedPendingIntents(service)

        service.onClick()

        assertEquals(1, started.size)
        assertOpensPasteAndOpen(started.single())
    }

    @Test
    fun `click on the lock screen unlocks first, then opens Paste & Open`() {
        val service = createService()
        val started = recordStartedPendingIntents(service)
        shadowOf(service).setLocked(true)

        service.onClick()

        // The shadow's unlockAndRun unlocks, then runs the action.
        assertFalse(service.isLocked)
        assertEquals(1, started.size)
        assertOpensPasteAndOpen(started.single())
    }

    @Test
    fun `pending intent is immutable and targets the non-exported alias`() {
        assertOpensPasteAndOpen(PasteAndOpenTileService.pasteAndOpenPendingIntent(app))
    }

    @Test
    fun `intent for Android 13 and lower starts the alias in a new task`() {
        val intent = OpenChatActivity.pasteAndOpenIntent(app)
        assertEquals(ComponentName(app, OpenChatActivity.PASTE_AND_OPEN_ALIAS), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `tile is shown as inactive`() {
        val service = createService()
        service.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)
    }

    @Test
    fun `manifest lets only System UI bind the tile`() {
        val info: ServiceInfo = app.packageManager.getServiceInfo(ComponentName(app, PasteAndOpenTileService::class.java), 0)
        assertTrue(info.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
        val tiles = app.packageManager.queryIntentServices(
            Intent(TileService.ACTION_QS_TILE).setPackage(app.packageName), 0
        )
        assertEquals(listOf(PasteAndOpenTileService::class.java.name), tiles.map { it.serviceInfo.name })
    }
}
