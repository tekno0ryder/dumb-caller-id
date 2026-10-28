package com.ahmedalsinan.dumbcallerid

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// The last few incoming calls and what happened on each. Shown on the main screen, so anyone can
// tell why a call did or didn't get a popup, and look up a number they missed.
object RecentCalls {
    // Stored by name, so the text follows the app language and old entries survive reordering.
    enum class Outcome(val text: Int, val color: Int) {
        POPUP(R.string.outcome_popup, R.color.success),
        POPUP_BLOCKED(R.string.outcome_popup_blocked, R.color.danger),
        DISABLED(R.string.outcome_disabled, R.color.text_secondary),
        NO_NUMBER(R.string.outcome_no_number, R.color.text_secondary),
    }

    class Call(val time: Long, val number: String?, val outcome: Outcome)

    private const val PREFS = "recent_calls"
    private const val KEY_CALLS = "calls"
    internal const val MAX_CALLS = 10

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(context: Context, number: String?, outcome: Outcome) {
        val calls = prepend(Call(System.currentTimeMillis(), number, outcome), load(context))
        prefs(context).edit().putString(KEY_CALLS, encode(calls)).apply()
    }

    // Newest first. The first call is also the current one while the phone is ringing or on a call.
    fun load(context: Context): List<Call> = decode(prefs(context).getString(KEY_CALLS, null))

    fun clear(context: Context) = prefs(context).edit().remove(KEY_CALLS).apply()

    internal fun prepend(call: Call, calls: List<Call>): List<Call> = (listOf(call) + calls).take(MAX_CALLS)

    internal fun encode(calls: List<Call>): String {
        val json = JSONArray()
        for (call in calls) {
            json.put(JSONObject().put("time", call.time).put("number", call.number ?: "").put("outcome", call.outcome.name))
        }
        return json.toString()
    }

    // Damaged data counts as no history rather than crashing the home screen on every launch, and entries
    // with an outcome this version doesn't know are dropped.
    internal fun decode(stored: String?): List<Call> = try {
        val json = JSONArray(stored ?: return emptyList())
        (0 until json.length()).mapNotNull { i ->
            val item = json.getJSONObject(i)
            val outcome = Outcome.entries.find { it.name == item.optString("outcome") } ?: return@mapNotNull null
            Call(item.getLong("time"), item.optString("number").ifEmpty { null }, outcome)
        }
    } catch (e: JSONException) {
        Log.w(LOG_TAG, "Recent calls unreadable, starting over", e)
        emptyList()
    }

    // Lets an open screen refresh as soon as a call comes in. Keep a strong reference to the
    // listener: SharedPreferences only holds it weakly.
    fun watch(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).registerOnSharedPreferenceChangeListener(listener)

    fun unwatch(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)

    // e.g. "Oct 26, 11:30 · popup shown".
    // e.g. "Just now · popup shown", "5 min. ago · …" within a day, then date and time; in the app language.
    // Refreshed whenever the home screen is shown.
    fun detail(context: Context, call: Call, now: Long = System.currentTimeMillis()): String {
        val age = now - call.time
        val whenText = when {
            age < DateUtils.MINUTE_IN_MILLIS -> context.getString(R.string.just_now)
            age < DateUtils.DAY_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(
                call.time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
            else -> DateUtils.formatDateTime(
                context, call.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
        }
        return context.getString(R.string.call_detail, whenText, context.getString(call.outcome.text))
    }
}
