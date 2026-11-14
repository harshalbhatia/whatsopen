package com.harshalbhatia.whatsopen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.harshalbhatia.whatsopen.databinding.DialogChooseWhatsappBinding

/** The WhatsApp apps a chat can open in, in default order of preference. */
enum class WhatsAppApp(val packageName: String, @StringRes val labelRes: Int) {
    CONSUMER("com.whatsapp", R.string.whatsapp_app_name),
    BUSINESS("com.whatsapp.w4b", R.string.whatsapp_business_app_name);

    companion object {
        fun fromPackage(packageName: String?): WhatsAppApp? = entries.firstOrNull { it.packageName == packageName }
    }
}

/** What [WhatsAppLauncher.openChat] did. */
sealed interface LaunchResult {
    /** WhatsApp was asked to open the chat. */
    data object Launched : LaunchResult

    /** Both apps are installed: a dialog asks which one to use, and opens the chat itself. */
    data object ChoosingApp : LaunchResult

    /** The number isn't valid. Nothing was opened; the user was told (see [WhatsAppLauncher.openChat]). */
    data object InvalidNumber : LaunchResult

    /** Neither app is installed and enabled. The user was told. */
    data object NotInstalled : LaunchResult

    /** Starting WhatsApp failed. The user was told. */
    data object Failed : LaunchResult

    /** Ignored because a chat was opened moments ago (a double tap). */
    data object Ignored : LaunchResult
}

object WhatsAppLauncher {

    private const val TAG = "WhatsAppLauncher"
    private const val PREFS_NAME = "whatsapp_launcher"
    private const val KEY_REMEMBERED_APP = "remembered_app"

    /** A second launch within this many milliseconds of the last one is ignored. */
    const val DEBOUNCE_MS = 700L

    /** Milliseconds from a monotonic clock. Replaceable in tests. */
    @VisibleForTesting
    internal var clock: () -> Long = SystemClock::elapsedRealtime

    private var lastLaunchAt: Long? = null

    /**
     * Opens a WhatsApp chat with [phoneNumber], written in any format [PhoneNumbers.normalize]
     * accepts. Numbers without a country code are read as numbers of [regionIso] (ISO 3166,
     * any case), or of [DefaultRegion] when that is null or blank. [message], if not empty,
     * is pre-filled in the chat.
     *
     * Uses the remembered app if it's available. Otherwise, when both WhatsApp and
     * WhatsApp Business are available and [context] is an Activity, asks which to use;
     * without an Activity it uses WhatsApp.
     *
     * Every failure is reported to the user: as a toast, or, when [onError] is given, by
     * passing the message's string resource to it instead, for a screen to show in its own UI.
     * That includes failing to start the app picked in the chooser, which happens later.
     *
     * When the result is [LaunchResult.ChoosingApp], [onChooserClosed] runs once the chooser
     * closes, whether an app was picked (after it was started) or not, unless the Activity is
     * destroyed first. It never runs for other results.
     */
    fun openChat(
        context: Context,
        phoneNumber: String,
        regionIso: String? = null,
        message: String? = null,
        onChooserClosed: (() -> Unit)? = null,
        onError: ((message: Int) -> Unit)? = null,
    ): LaunchResult {
        val report = onError ?: { toast(context, it) }
        val region = DefaultRegion.cleanIso(regionIso) ?: DefaultRegion.get(context)
        val e164 = PhoneNumbers.normalize(phoneNumber, region)
        if (e164 == null) {
            report(R.string.error_invalid_phone_number)
            return LaunchResult.InvalidNumber
        }

        val now = clock()
        val last = lastLaunchAt
        if (last != null && now - last in 0 until DEBOUNCE_MS) return LaunchResult.Ignored

        val activity = context.findActivity()?.takeUnless { it.isFinishing || it.isDestroyed }
        val uri = buildChatUri(e164, message)
        return when (val choice = chooseApps(availableApps(context), rememberedApp(context), activity != null)) {
            AppChoice.NoneAvailable -> {
                report(R.string.error_whatsapp_not_installed)
                LaunchResult.NotInstalled
            }
            is AppChoice.Use -> launch(context, uri, choice.apps, report)
            is AppChoice.Ask ->
                if (activity != null && showChooser(activity, uri, choice.apps, onChooserClosed, report)) {
                    LaunchResult.ChoosingApp
                } else {
                    launch(context, uri, choice.apps, report)
                }
        }.also { result ->
            // Only a chat that opened (or a chooser that's showing) blocks a quick second tap;
            // after a failure the user can retry straight away.
            if (result == LaunchResult.Launched || result == LaunchResult.ChoosingApp) lastLaunchAt = now
        }
    }

    /**
     * The click-to-chat link for an E.164 number: https://api.whatsapp.com/send?phone=14155552671
     * (digits without "+"), with &text= when [message] isn't empty. Both values are URL-encoded.
     */
    fun buildChatUri(e164: String, message: String? = null): Uri =
        Uri.Builder()
            .scheme("https")
            .authority("api.whatsapp.com")
            .path("/send")
            .appendQueryParameter("phone", e164.removePrefix("+"))
            .apply { if (!message.isNullOrEmpty()) appendQueryParameter("text", message) }
            .build()

    /** The app the user chose to always use, or null. */
    fun rememberedApp(context: Context): WhatsAppApp? =
        WhatsAppApp.fromPackage(prefs(context).getString(KEY_REMEMBERED_APP, null))

    /** Forgets the remembered app, so the next chat asks again. Returns true if one was remembered. */
    fun clearRememberedApp(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_REMEMBERED_APP)) return false
        prefs.edit().remove(KEY_REMEMBERED_APP).apply()
        return true
    }

    /** Forgets the last launch time and restores the real clock. */
    @VisibleForTesting
    internal fun resetForTesting() {
        lastLaunchAt = null
        clock = SystemClock::elapsedRealtime
    }

    internal sealed interface AppChoice {
        /** Try these apps in order. */
        data class Use(val apps: List<WhatsAppApp>) : AppChoice

        /** Ask the user to pick one of these apps. */
        data class Ask(val apps: List<WhatsAppApp>) : AppChoice

        data object NoneAvailable : AppChoice
    }

    /** Decides which app to use from those installed and enabled ([available]). */
    internal fun chooseApps(available: List<WhatsAppApp>, remembered: WhatsAppApp?, canAsk: Boolean): AppChoice =
        when {
            available.isEmpty() -> AppChoice.NoneAvailable
            remembered != null && remembered in available -> AppChoice.Use(listOf(remembered) + (available - remembered))
            available.size > 1 && canAsk -> AppChoice.Ask(available)
            else -> AppChoice.Use(available)
        }

    internal fun availableApps(context: Context): List<WhatsAppApp> =
        WhatsAppApp.entries.filter { isInstalledAndEnabled(context.packageManager, it.packageName) }

    private fun isInstalledAndEnabled(packageManager: PackageManager, packageName: String): Boolean =
        try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    /** Starts the first app in [apps] that can open [uri]. Passes the error message to [report] if none can. */
    private fun launch(
        context: Context,
        uri: Uri,
        apps: List<WhatsAppApp>,
        report: (message: Int) -> Unit,
    ): LaunchResult {
        val newTask = context.findActivity() == null
        for (app in apps) {
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(app.packageName)
            if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return LaunchResult.Launched
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "${app.name} can't open chats")
            } catch (e: RuntimeException) {
                // The exception's message can contain the chat link, which contains the number.
                Log.e(TAG, "Error opening WhatsApp: ${e.javaClass.name}")
                break
            }
        }
        report(R.string.error_opening_whatsapp)
        return LaunchResult.Failed
    }

    /** Shows the app chooser. Returns false if the dialog can't be shown (e.g. a non-Material theme). */
    private fun showChooser(
        activity: Activity,
        uri: Uri,
        apps: List<WhatsAppApp>,
        onClosed: (() -> Unit)?,
        report: (message: Int) -> Unit,
    ): Boolean {
        return try {
            val builder = MaterialAlertDialogBuilder(activity)
            val binding = DialogChooseWhatsappBinding.inflate(LayoutInflater.from(builder.context))
            val labels = apps.map { activity.getString(it.labelRes) }.toTypedArray()
            builder
                .setTitle(R.string.choose_whatsapp_title)
                .setItems(labels) { _, which ->
                    val app = apps[which]
                    if (binding.rememberChoice.isChecked) {
                        prefs(activity).edit().putString(KEY_REMEMBERED_APP, app.packageName).apply()
                    }
                    launch(activity, uri, listOf(app) + (apps - app), report)
                }
                .setView(binding.root)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener { onClosed?.invoke() }
                .show()
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Can't show the app chooser: ${e.javaClass.name}")
            false
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun toast(context: Context, @StringRes message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun Context.findActivity(): Activity? {
        var context: Context? = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
