package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Things set once and rarely revisited: the bot, a popup test, the setup checklist and the version.
class SettingsActivity : Activity() {
    private lateinit var botInput: EditText
    private lateinit var botStatus: TextView
    private lateinit var testStatus: TextView
    private lateinit var checklist: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        botInput = findViewById(R.id.botInput)
        botStatus = findViewById(R.id.botStatus)
        testStatus = findViewById(R.id.testStatus)
        checklist = findViewById(R.id.checklist)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        botInput.setText(Prefs.botName(this))

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (Prefs.setBotName(this, botInput.text.toString())) {
                botInput.setText(Prefs.botName(this))
                showStatus(botStatus, R.string.saved)
            } else {
                showStatus(botStatus, R.string.invalid_bot)
            }
        }
        findViewById<Button>(R.id.testButton).setOnClickListener {
            val shown = CallOverlay.canShow(this) && CallOverlay.show(this, TEST_NUMBER)
            showStatus(testStatus, if (shown) R.string.test_popup_sent else R.string.test_popup_blocked)
        }

        findViewById<Button>(R.id.copyDiagnostics).setOnClickListener {
            val clip = ClipData.newPlainText(getString(R.string.copy_diagnostics), Diagnostics.collect(this))
            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            // Android 13+ confirms copies itself.
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.sourceCodeUrl).text = getString(R.string.repo_url).removePrefix("https://")
        findViewById<View>(R.id.sourceCode).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.repo_url))))
            } catch (e: ActivityNotFoundException) { // no browser
                Toast.makeText(this, R.string.repo_url, Toast.LENGTH_LONG).show()
            }
        }

        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        findViewById<TextView>(R.id.version).text = getString(if (debug) R.string.version_debug else R.string.version, version)
        findViewById<Button>(R.id.checkUpdates).setOnClickListener { checkForUpdate(it) }
    }

    // Checks right away instead of waiting for the daily check.
    private fun checkForUpdate(button: View) {
        button.isEnabled = false
        val app = applicationContext
        Thread {
            val reached = Updates.check(app)
            runOnUiThread {
                button.isEnabled = true
                when {
                    !reached -> Toast.makeText(app, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                    Updates.available(app) != null -> startActivity(Intent(this, MainActivity::class.java)) // its banner
                    else -> Toast.makeText(app, R.string.update_latest, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        renderChecklist()
        Setup.confirmPendingStep(this, ::renderChecklist)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Setup.onPermissionsResult(this, requestCode)
        renderChecklist()
    }

    private fun showStatus(view: TextView, text: Int) {
        view.setText(text)
        view.visibility = View.VISIBLE
    }

    private fun renderChecklist() {
        checklist.removeAllViews()
        for ((index, check) in Setup.checks.withIndex()) {
            val ok = check.isOk(this)
            val row = layoutInflater.inflate(R.layout.check_row, checklist, false)
            row.findViewById<ImageView>(R.id.checkIcon).setImageResource(Setup.icon(check, this))
            row.findViewById<TextView>(R.id.checkTitle).setText(check.title)
            row.findViewById<Button>(R.id.checkFix).apply {
                visibility = if (ok || !check.canFix(this@SettingsActivity)) View.GONE else View.VISIBLE
                // That step's setup page: why it's needed, what to do, and tips if it doesn't work.
                setOnClickListener { startActivity(OnboardingActivity.forStep(this@SettingsActivity, index)) }
            }
            checklist.addView(row)
        }
    }

    private companion object {
        const val TEST_NUMBER = "+966500000000"
    }
}
