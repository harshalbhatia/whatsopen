package com.harshalbhatia.whatsopen

import android.app.Application
import com.google.android.material.color.DynamicColors

class WhatsOpenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
