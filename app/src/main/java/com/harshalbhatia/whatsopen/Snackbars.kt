package com.harshalbhatia.whatsopen

import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Shows [message] in a short snackbar, above the bottom navigation when it's showing, otherwise
 * above the keyboard or navigation bar. This is how the tabs give feedback; toasts are left for
 * code that has no screen of its own.
 *
 * Does nothing and returns null if the fragment has no view, e.g. when a callback arrives
 * after the user left the tab.
 */
fun Fragment.showSnackbar(@StringRes message: Int): Snackbar? {
    val view = view ?: return null
    return Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
        // MainActivity hides the bottom navigation while the keyboard is up.
        .setAnchorView(activity?.findViewById<View>(R.id.bottom_navigation)?.takeIf { it.isShown })
        .apply { show() }
}
