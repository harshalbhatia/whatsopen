package com.example.whatsopen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast

object WhatsAppLauncher {

    private const val TAG = "WhatsAppLauncher"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun openChat(context: Context, phoneNumber: String) {
        val targetPackage = when {
            isPackageInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
            isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
            else -> {
                Toast.makeText(
                    context,
                    "WhatsApp is not installed on your device",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        try {
            val uri = buildWhatsAppUri(phoneNumber)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(targetPackage)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WhatsApp chat for number: $phoneNumber", e)
            Toast.makeText(context, "Error opening WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildWhatsAppUri(phoneNumber: String): Uri {
        val encodedNumber = Uri.encode(phoneNumber)
        return Uri.parse("https://api.whatsapp.com/send?phone=$encodedNumber")
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
