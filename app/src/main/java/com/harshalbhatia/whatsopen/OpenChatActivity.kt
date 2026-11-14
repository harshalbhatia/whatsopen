package com.harshalbhatia.whatsopen

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

/**
 * Opens a WhatsApp chat from outside the app without bringing up the app: from shared text,
 * tel: links and selected text (PROCESS_TEXT), or from the clipboard when started through
 * [PASTE_AND_OPEN_ALIAS] (Paste & Open).
 *
 * Text holding exactly one number opens that chat; other text goes to the Clipboard tab for the
 * user to pick from. It has no UI of its own besides the WhatsApp chooser and finishes once done.
 */
class OpenChatActivity : AppCompatActivity() {

    /** Paste & Open is waiting for window focus, without which Android 10+ hides the clipboard. */
    private var waitingForFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            finish()
            return
        }
        // Decided by component, not action: any app can send the action to this exported activity.
        if (componentName.className == PASTE_AND_OPEN_ALIAS) {
            waitingForFocus = true
        } else {
            handleIncomingIntent(intent)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Only the first time: focus also comes back when the chooser dialog closes.
        if (!hasFocus || !waitingForFocus) return
        waitingForFocus = false
        val text = readClipboard()
        if (text == null) {
            toast(R.string.error_clipboard_empty)
            finish()
        } else {
            handleText(text)
        }
    }

    /**
     * Starts everything in its own task. This activity's task is hidden from Recents and ends
     * when it finishes, so a chat opened in it could be hard to get back to.
     */
    override fun startActivity(intent: Intent, options: Bundle?) {
        super.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_DIAL) {
            val region = DefaultRegion.get(this)
            val number = IncomingIntentParser.extractPhoneNumber(intent, region)
            if (number != null) {
                openChat(number, region)
            } else {
                toast(R.string.error_link_without_number)
                finish()
            }
            return
        }

        val text = try {
            IncomingIntentParser.sharedText(intent)
        } catch (e: RuntimeException) {
            // Another app can send extras that fail to unparcel (BadParcelableException).
            null
        }
        if (text != null) handleText(text) else finish()
    }

    private fun handleText(text: String) {
        val region = DefaultRegion.get(this)
        val number = IncomingIntentParser.singleNumber(text, region)
        if (number != null) {
            openChat(number, region)
        } else {
            PendingShare.text = text
            startActivity(MainActivity.clipboardTabIntent(this))
            finish()
        }
    }

    private fun openChat(number: String, region: String?) {
        val result = WhatsAppLauncher.openChat(this, number, region, onChooserClosed = ::finish)
        // The chooser dialog lives in this activity, so it stays until the dialog closes.
        if (result != LaunchResult.ChoosingApp) finish()
    }

    /** The clipboard's text, cut like shared text, or null if there's none. */
    private fun readClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val text = try {
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        } catch (e: RuntimeException) {
            // Reading a clip that points at another app's content can throw SecurityException.
            null
        } ?: return null
        return PhoneNumbers.capText(text).takeIf { it.isNotBlank() }
    }

    private fun toast(@StringRes message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /**
         * The non-exported alias of this activity that does Paste & Open. Declared in
         * AndroidManifest.xml and targeted by res/xml/shortcuts.xml.
         */
        const val PASTE_AND_OPEN_ALIAS = "com.harshalbhatia.whatsopen.PasteAndOpenActivity"

        /** Action of Paste & Open intents. Only descriptive: the alias is what selects the mode. */
        const val ACTION_PASTE_AND_OPEN = "com.harshalbhatia.whatsopen.action.PASTE_AND_OPEN"

        /** Reads the clipboard and opens the chat for the number on it (Paste & Open). */
        fun pasteAndOpenIntent(context: Context): Intent =
            Intent(ACTION_PASTE_AND_OPEN)
                .setClassName(context, PASTE_AND_OPEN_ALIAS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
