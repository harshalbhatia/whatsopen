package com.harshalbhatia.whatsopen

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that opens a chat with the number on the clipboard (Paste & Open). */
class PasteAndOpenTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // An action, not a toggle, so it's never shown as on.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openPasteAndOpen() } else openPasteAndOpen()
    }

    // Lint flags the Intent overload even behind the version check; it only runs below Android 14,
    // which has no PendingIntent overload.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPasteAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pasteAndOpenPendingIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(OpenChatActivity.pasteAndOpenIntent(this))
        }
    }

    companion object {
        /** Paste & Open as an immutable PendingIntent, for Android 14+. */
        fun pasteAndOpenPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                OpenChatActivity.pasteAndOpenIntent(context),
                PendingIntent.FLAG_IMMUTABLE
            )
    }
}
