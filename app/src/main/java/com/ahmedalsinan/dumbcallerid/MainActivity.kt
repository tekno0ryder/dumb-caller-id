package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// Home: is it working, and what happened on recent calls. Everything else is in SettingsActivity.
class MainActivity : Activity() {
    private lateinit var enabledSwitch: Switch
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusSummary: TextView
    private lateinit var setupWarning: View
    private lateinit var recentCalls: LinearLayout
    private lateinit var noCalls: View
    private lateinit var clearCalls: View
    private val callsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> renderRecentCalls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enabledSwitch = findViewById(R.id.enabledSwitch)
        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusSummary = findViewById(R.id.statusSummary)
        setupWarning = findViewById(R.id.setupWarning)
        recentCalls = findViewById(R.id.recentCalls)
        noCalls = findViewById(R.id.noCalls)
        clearCalls = findViewById(R.id.clearCalls)
        clearCalls.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.clear_calls_confirm)
                .setPositiveButton(R.string.clear) { _, _ -> RecentCalls.clear(this) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        findViewById<View>(R.id.settingsButton).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        val openOnboarding = View.OnClickListener { startActivity(Intent(this, OnboardingActivity::class.java)) }
        setupWarning.setOnClickListener(openOnboarding)
        findViewById<View>(R.id.setupFix).setOnClickListener(openOnboarding)

        enabledSwitch.isChecked = Prefs.isEnabled(this)
        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
            PopupTile.refresh(this)
            renderStatus()
        }

        // First launch only; later, missing steps show as the "Needs setup" warning.
        if (savedInstanceState == null && !Prefs.onboardingSeen(this) && !Setup.isComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // May have changed from the quick settings tile while the app was in the background.
        enabledSwitch.isChecked = Prefs.isEnabled(this)
        renderStatus()
        renderRecentCalls()
        RecentCalls.watch(this, callsListener)
    }

    override fun onPause() {
        super.onPause()
        RecentCalls.unwatch(this, callsListener)
    }

    private fun renderStatus() {
        val supported = Setup.isSupported(this)
        val complete = Setup.isComplete(this)
        val status = HomeStatus.of(supported, enabledSwitch.isChecked, complete)
        statusTitle.setText(status.title)
        statusSummary.setText(status.summary)
        statusDot.backgroundTintList = ColorStateList.valueOf(getColor(status.color))
        setupWarning.visibility = if (showsSetupWarning(supported, complete)) View.VISIBLE else View.GONE
    }

    private fun renderRecentCalls() {
        val calls = RecentCalls.load(this)
        recentCalls.removeAllViews()
        for (call in calls) {
            val row = layoutInflater.inflate(R.layout.call_row, recentCalls, false)
            row.findViewById<TextView>(R.id.callNumber).text =
                call.number?.let { PhoneNumbers.format(this, it) } ?: getString(R.string.no_number)
            row.findViewById<TextView>(R.id.callDetail).apply {
                text = RecentCalls.detail(this@MainActivity, call)
                setTextColor(getColor(call.outcome.color))
            }
            row.findViewById<Button>(R.id.callLookup).apply {
                val number = call.number
                visibility = if (number != null) View.VISIBLE else View.GONE
                setOnClickListener { number?.let { Telegram.lookUp(this@MainActivity, it) } }
            }
            recentCalls.addView(row)
        }
        recentCalls.visibility = if (calls.isEmpty()) View.GONE else View.VISIBLE
        noCalls.visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
        clearCalls.visibility = if (calls.isEmpty()) View.GONE else View.VISIBLE
    }
}

// The home screen's one-line answer to "will calls get a popup?".
enum class HomeStatus(val title: Int, val summary: Int, val color: Int) {
    UNSUPPORTED(R.string.status_unsupported, R.string.unsupported_summary, R.color.danger),
    OFF(R.string.status_off, R.string.disabled_summary, R.color.text_secondary),
    NEEDS_SETUP(R.string.status_needs_setup, R.string.enabled_summary, R.color.danger),
    ACTIVE(R.string.status_active, R.string.enabled_summary, R.color.success);

    companion object {
        fun of(supported: Boolean, enabled: Boolean, setupComplete: Boolean): HomeStatus = when {
            !supported -> UNSUPPORTED
            !enabled -> OFF
            !setupComplete -> NEEDS_SETUP
            else -> ACTIVE
        }
    }
}

// The "Some setup steps are missing" box, shown even while switched off so setup can be finished first. Not on
// unsupported phones, where no step can make the popup work.
fun showsSetupWarning(supported: Boolean, setupComplete: Boolean): Boolean = supported && !setupComplete
