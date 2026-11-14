package com.harshalbhatia.whatsopen

import android.app.Activity
import android.widget.TextView
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * Forgets the snackbar left showing by an earlier test. Material keeps the current snackbar in
 * a static queue, so without this a new one waits behind a snackbar whose test has ended.
 */
fun resetSnackbars() {
    ReflectionHelpers.setStaticField(
        Class.forName("com.google.android.material.snackbar.SnackbarManager"), "snackbarManager", null
    )
}

/** The text of the snackbar showing in [activity], or null if there is none. */
fun snackbarText(activity: Activity): String? {
    ShadowLooper.idleMainLooper()
    return activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString()
}
