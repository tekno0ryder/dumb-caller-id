package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
    private lateinit var hints: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        botInput = findViewById(R.id.botInput)
        botStatus = findViewById(R.id.botStatus)
        testStatus = findViewById(R.id.testStatus)
        checklist = findViewById(R.id.checklist)
        hints = findViewById(R.id.hints)

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

        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.version).text = getString(R.string.version, version)
    }

    override fun onResume() {
        super.onResume()
        renderChecklist()
        Setup.confirmPendingStep(this, ::renderChecklist)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Setup.REQUEST_PERMISSIONS) Setup.onPermissionsResult(this)
        renderChecklist()
    }

    private fun showStatus(view: TextView, text: Int) {
        view.setText(text)
        view.visibility = View.VISIBLE
    }

    private fun renderChecklist() {
        checklist.removeAllViews()
        for (check in Setup.checks) {
            val ok = check.isOk(this)
            val row = layoutInflater.inflate(R.layout.check_row, checklist, false)
            row.findViewById<ImageView>(R.id.checkIcon).setImageResource(if (ok) R.drawable.ic_check_ok else R.drawable.ic_check_missing)
            row.findViewById<TextView>(R.id.checkTitle).setText(check.title)
            row.findViewById<Button>(R.id.checkFix).apply {
                visibility = if (ok || !check.canFix(this@SettingsActivity)) View.GONE else View.VISIBLE
                setOnClickListener { check.fix(this@SettingsActivity) }
            }
            checklist.addView(row)
        }

        val notes = Setup.hints(this)
        hints.text = notes.joinToString("\n\n")
        hints.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
    }

    private companion object {
        const val TEST_NUMBER = "+966500000000"
    }
}
