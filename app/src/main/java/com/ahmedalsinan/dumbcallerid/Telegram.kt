package com.ahmedalsinan.dumbcallerid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast

// Opening the bot chat with the caller's number pre-filled.
object Telegram {
    // Play Store build, then the one from telegram.org. Both must be listed in the manifest's <queries>.
    private val PACKAGES = listOf("org.telegram.messenger", "org.telegram.messenger.web")

    // Telegram usernames: 5-32 chars, letters, digits and underscores.
    private val USERNAME = Regex("[A-Za-z0-9_]{5,32}")

    // Accepts "MyBot", "@MyBot" or a pasted "https://t.me/MyBot" link. Returns the bare username,
    // "" for empty input (use the default bot), or null when it isn't a valid username.
    fun parseBotName(input: String): String? {
        val name = input.trim().substringBefore('?').trimEnd('/').substringAfterLast('/').removePrefix("@")
        return name.takeIf { it.isEmpty() || it.matches(USERNAME) }
    }

    fun isInstalled(context: Context): Boolean = PACKAGES.any {
        try {
            context.packageManager.getPackageInfo(it, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // The tg:// link targeted at Telegram skips the app chooser; https is the fallback.
    internal fun lookupIntent(context: Context, number: String): Intent {
        val bot = Prefs.botName(context)
        val text = Uri.encode(number)
        for (pkg in PACKAGES) {
            val direct = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$bot&text=$text"))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (direct.resolveActivity(context.packageManager) != null) return direct
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$bot?text=$text"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun lookUp(context: Context, number: String) {
        try {
            context.startActivity(lookupIntent(context, number))
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "Nothing can open the lookup link", e)
            Toast.makeText(context, R.string.telegram_missing, Toast.LENGTH_SHORT).show()
        }
    }
}
