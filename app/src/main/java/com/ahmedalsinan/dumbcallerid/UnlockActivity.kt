package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

// Invisible screen that asks the user to unlock, then opens the bot. Opening Telegram straight from the
// popup on a locked phone launches it silently behind the lock screen, with no unlock prompt.
class UnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: return finish()

        setShowWhenLocked(true)

        val keyguard = getSystemService(KeyguardManager::class.java)
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                Telegram.lookUp(this@UnlockActivity, number)
                finish()
            }

            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }

    companion object {
        private const val EXTRA_NUMBER = "number"

        fun intent(context: Context, number: String): Intent =
            Intent(context, UnlockActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
