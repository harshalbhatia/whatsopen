package com.harshalbhatia.whatsopen

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `adaptive icons draw the glyph and the themed icon from vectors`() {
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = ContextCompat.getDrawable(app, icon) as AdaptiveIconDrawable
            assertTrue(drawable.foreground is VectorDrawable)
            // A full-colour bitmap here gives a solid square as the themed icon.
            assertTrue(drawable.monochrome is VectorDrawable)
        }
    }

    @Test
    fun `the old foreground bitmaps are gone`() {
        assertEquals(0, app.resources.getIdentifier("ic_launcher_foreground", "mipmap", app.packageName))
    }
}
