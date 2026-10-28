package com.ahmedalsinan.dumbcallerid

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TelegramIntentTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun opensTheBotInTelegramWhenInstalled() {
        Prefs.setBotName(context, "MyLookupBot")
        installTelegram("org.telegram.messenger")

        val intent = Telegram.lookupIntent(context, "+966500000000")
        assertEquals("tg://resolve?domain=MyLookupBot&text=%2B966500000000", intent.data.toString())
        assertEquals("org.telegram.messenger", intent.`package`)
        assertTrue(Telegram.isInstalled(context))
    }

    @Test
    fun usesTheTelegramWebsiteBuildToo() {
        installTelegram("org.telegram.messenger.web")
        assertEquals("org.telegram.messenger.web", Telegram.lookupIntent(context, "+1").`package`)
    }

    @Test
    fun fallsBackToTheWebLinkWithoutTelegram() {
        val intent = Telegram.lookupIntent(context, "+966500000000")
        assertEquals("https://t.me/dlilcomApp_bot?text=%2B966500000000", intent.data.toString())
        assertNull(intent.`package`)
        assertFalse(Telegram.isInstalled(context))
    }

    // An installed Telegram build whose main screen opens tg:// links, as the real app's does.
    private fun installTelegram(pkg: String) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply { packageName = pkg })
        val component = ComponentName(pkg, "$pkg.LaunchActivity")
        pm.addActivityIfNotPresent(component).exported = true
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("tg")
        })
    }
}
