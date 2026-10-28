package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

// Walks through the missing setup steps one at a time: why it's needed, what to do next, then one button to do it.
// Opens by itself on the first launch, and from the home screen's "Needs setup" warning.
class OnboardingActivity : Activity() {
    // Indexes into Setup.checks that were missing when this screen opened, so "Step 2 of 3" stays stable.
    private lateinit var steps: IntArray
    private val skipped = mutableSetOf<Int>()

    private lateinit var progress: TextView
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var next: TextView
    private lateinit var hintGroup: View
    private lateinit var hint: TextView
    private lateinit var primary: Button
    private lateinit var skip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        progress = findViewById(R.id.progress)
        icon = findViewById(R.id.icon)
        title = findViewById(R.id.title)
        body = findViewById(R.id.body)
        next = findViewById(R.id.next)
        hintGroup = findViewById(R.id.hintGroup)
        hint = findViewById(R.id.hint)
        primary = findViewById(R.id.primary)
        skip = findViewById(R.id.skip)

        steps = savedInstanceState?.getIntArray(KEY_STEPS)
            ?: Setup.checks.indices.filter { !Setup.checks[it].isOk(this) }.toIntArray()
        savedInstanceState?.getIntArray(KEY_SKIPPED)?.let { skipped += it.toList() }
        Prefs.setOnboardingSeen(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_STEPS, steps)
        outState.putIntArray(KEY_SKIPPED, skipped.toIntArray())
    }

    // Also runs when coming back from a system settings screen, which moves on to the next step.
    override fun onResume() {
        super.onResume()
        render()
        Setup.confirmPendingStep(this, ::render)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Setup.REQUEST_PERMISSIONS) Setup.onPermissionsResult(this)
        render()
    }

    private fun render() {
        if (!Setup.isSupported(this)) return renderUnsupported()
        val position = nextStep(steps, skipped) { Setup.checks[it].isOk(this) }
        if (position == -1) return renderDone()

        val check = Setup.checks[steps[position]]
        progress.text = getString(R.string.onboarding_step, position + 1, steps.size)
        progress.visibility = View.VISIBLE
        icon.setImageResource(R.drawable.ic_check_missing)
        title.setText(check.title)
        body.setText(check.why)
        next.text = getString(check.next, getString(R.string.app_name))
        next.visibility = View.VISIBLE
        val stepHint = check.hint(this)
        hint.text = stepHint
        hintGroup.visibility = if (stepHint == null) View.GONE else View.VISIBLE
        primary.setText(check.action)
        primary.setOnClickListener { check.fix(this) }
        skip.visibility = View.VISIBLE
        skip.setOnClickListener {
            skipped += steps[position]
            render()
        }
    }

    // Skipped steps leave the home screen showing "Needs setup", so they can be finished later.
    private fun renderDone() {
        val complete = Setup.isComplete(this)
        progress.visibility = View.INVISIBLE
        icon.setImageResource(if (complete) R.drawable.ic_check_ok else R.drawable.ic_check_missing)
        title.setText(if (complete) R.string.onboarding_done else R.string.onboarding_later)
        body.setText(if (complete) R.string.onboarding_done_body else R.string.onboarding_later_body)
        next.visibility = View.GONE
        hintGroup.visibility = View.GONE
        primary.setText(R.string.onboarding_finish)
        primary.setOnClickListener { finish() }
        skip.visibility = View.GONE
    }

    private fun renderUnsupported() {
        progress.visibility = View.INVISIBLE
        icon.setImageResource(R.drawable.ic_check_missing)
        title.setText(R.string.status_unsupported)
        body.setText(R.string.unsupported_summary)
        next.visibility = View.GONE
        hintGroup.visibility = View.GONE
        primary.setText(R.string.onboarding_finish)
        primary.setOnClickListener { finish() }
        skip.visibility = View.GONE
    }

    private companion object {
        const val KEY_STEPS = "steps"
        const val KEY_SKIPPED = "skipped"
    }
}

// Position in [steps] (indexes into Setup.checks) of the step to show: the first that's neither done nor skipped.
// -1 when none is left. Steps done meanwhile, e.g. in a settings screen, are passed over.
fun nextStep(steps: IntArray, skipped: Set<Int>, isDone: (Int) -> Boolean): Int =
    steps.indexOfFirst { it !in skipped && !isDone(it) }
