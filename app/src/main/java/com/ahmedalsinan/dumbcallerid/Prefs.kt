package com.ahmedalsinan.dumbcallerid

import android.content.Context

// The user's settings and one-time flags.
object Prefs {
    private const val PREFS = "settings"
    private const val KEY_BOT = "bot_name"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    private const val KEY_PENDING_STEP = "pending_step"
    private const val DEFAULT_BOT = "dlilcomApp_bot"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // When off, incoming calls are only recorded in recent calls, with no popup.
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    // The step-by-step setup opens by itself only once, on the first launch.
    fun onboardingSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_SEEN, false)

    fun setOnboardingSeen(context: Context) = prefs(context).edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()

    // Maker-specific setup steps that apps can't check, stored under each step's key once the user confirms
    // (see ManualStep).
    fun isConfirmed(context: Context, key: String): Boolean = prefs(context).getBoolean(key, false)

    fun setConfirmed(context: Context, key: String) = prefs(context).edit().putBoolean(key, true).apply()

    // The manual step whose "Did you do it?" question is waiting for an answer, if any.
    fun pendingStep(context: Context): String? = prefs(context).getString(KEY_PENDING_STEP, null)

    fun setPendingStep(context: Context, key: String) = prefs(context).edit().putString(KEY_PENDING_STEP, key).apply()

    fun clearPendingStep(context: Context) = prefs(context).edit().remove(KEY_PENDING_STEP).apply()

    fun botName(context: Context): String = prefs(context).getString(KEY_BOT, null) ?: DEFAULT_BOT

    // Returns false (and saves nothing) when the input is not a valid username. Empty input restores the default.
    fun setBotName(context: Context, input: String): Boolean {
        val name = Telegram.parseBotName(input) ?: return false
        val editor = prefs(context).edit()
        if (name.isEmpty()) editor.remove(KEY_BOT) else editor.putString(KEY_BOT, name)
        editor.apply()
        return true
    }
}
