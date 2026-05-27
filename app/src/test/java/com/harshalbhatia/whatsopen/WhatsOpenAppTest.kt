package com.harshalbhatia.whatsopen

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = WhatsOpenApp::class)
class WhatsOpenAppTest {

    @Test
    fun `application class is WhatsOpenApp`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue("expected WhatsOpenApp, got ${app::class.simpleName}", app is WhatsOpenApp)
    }

    @Test
    fun `application onCreate runs without crashing`() {
        // Robolectric runs onCreate automatically. Reaching this point means DynamicColors didn't crash.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertNotNull(app)
    }
}
