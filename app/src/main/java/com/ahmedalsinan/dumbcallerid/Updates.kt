package com.ahmedalsinan.dumbcallerid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// An APK shared outside an app store never updates itself, so the app checks GitHub releases once a day and
// installs a newer one on request. The app's only network use: one request for the latest release, which sends
// nothing about the user or their calls.
object Updates {
    private const val PREFS = "updates"
    private const val KEY_TAG = "tag"
    private const val KEY_APK_URL = "apk_url"
    private const val KEY_CHECKED = "checked"
    private const val KEY_NOTIFIED = "notified"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 1
    private const val JOB_ID = 1

    // Opening the app checks again if the daily job hasn't run for this long.
    private const val STALE_AFTER_MS = 6 * DateUtils.HOUR_IN_MILLIS
    private const val TIMEOUT_MS = 15_000

    data class Release(val tag: String, val apkUrl: String)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    // The newer release found by the last check, or null. Rechecked against the installed version, so it clears
    // itself once the update is installed.
    fun available(context: Context): Release? {
        val tag = prefs(context).getString(KEY_TAG, null) ?: return null
        val url = prefs(context).getString(KEY_APK_URL, null) ?: return null
        return Release(tag, url).takeIf { isNewer(it.tag, installedVersion(context)) }
    }

    // The latest published release with an APK attached, from GitHub's "latest release" response.
    internal fun parseLatest(json: String): Release? = try {
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            null
        } else {
            val assets = release.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
            apk?.let { Release(release.getString("tag_name"), it.getString("browser_download_url")) }
        }
    } catch (e: JSONException) {
        null
    }

    // "v1.10" is newer than "1.9". A version that isn't plain numbers never counts as newer.
    internal fun isNewer(tag: String, current: String): Boolean {
        val candidate = numbers(tag) ?: return false
        val installed = numbers(current) ?: return false
        for (i in 0 until maxOf(candidate.size, installed.size)) {
            val a = candidate.getOrElse(i) { 0 }
            val b = installed.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbers(version: String): List<Int>? =
        version.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }

    // Network: call off the main thread. Failures (offline, GitHub down) are logged and retried next time.
    // False if GitHub couldn't be reached, so a manual check doesn't claim "latest version" when offline.
    fun check(context: Context): Boolean = check(context, ::get)

    // [fetch] returns the response body, or null for anything but HTTP 200; tests pass a fake one.
    internal fun check(context: Context, fetch: (String) -> String?): Boolean {
        try {
            // GitHub's "latest release" for release builds; this computer for debug ones (see app/build.gradle.kts).
            val release = fetch(context.getString(R.string.update_url))?.let(::parseLatest)
            val editor = prefs(context).edit().putLong(KEY_CHECKED, System.currentTimeMillis())
            if (release != null && isNewer(release.tag, installedVersion(context))) {
                editor.putString(KEY_TAG, release.tag).putString(KEY_APK_URL, release.apkUrl)
                // Once per version, not on every daily check.
                if (prefs(context).getString(KEY_NOTIFIED, null) != release.tag && notify(context, release)) {
                    editor.putString(KEY_NOTIFIED, release.tag)
                }
            } else {
                editor.remove(KEY_TAG).remove(KEY_APK_URL)
                clearNotification(context)
            }
            editor.apply()
            return true
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Update check failed", e)
            return false
        }
    }

    // "Update available: v1.2"; tapping it opens the home screen, whose banner has the Update button. Returns
    // false if notifications are off (the optional setup step), so the next check tries again.
    private fun notify(context: Context, release: Release): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.update_available, release.tag))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    // Once the update is installed (or no longer offered), the notification would be stale.
    fun clearNotification(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    // For screens: checks in the background if the last check is old, then calls [onDone] on the main thread.
    fun checkIfStale(context: Context, onDone: () -> Unit) {
        val checked = prefs(context).getLong(KEY_CHECKED, 0)
        if (System.currentTimeMillis() - checked < STALE_AFTER_MS) return
        val app = context.applicationContext
        Thread {
            check(app)
            android.os.Handler(app.mainLooper).post(onDone)
        }.start()
    }

    // A daily check whenever the phone is online. Safe to call repeatedly; it's called each time the app starts,
    // including for every incoming call, so it must never throw: Android refusing the job (e.g. Android 17 wants
    // ACCESS_NETWORK_STATE for the online condition) only costs the update check, never call handling.
    fun schedule(context: Context) {
        try {
            val jobs = context.getSystemService(JobScheduler::class.java)
            if (jobs.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(DateUtils.DAY_IN_MILLIS)
                .build()
            jobs.schedule(job)
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Daily update check not scheduled", e)
        }
    }

    // Downloads the release and hands it to Android's installer, which asks the user to confirm (except for later
    // updates on Android 12+, see below). Network: call off the main thread. False if it couldn't be started.
    fun install(context: Context, release: Release): Boolean = try {
        val apk = File(context.cacheDir, "update.apk")
        download(release.apkUrl, apk)
        if (isUpgradeOfThisApp(context, apk)) {
            commit(context, apk)
            true
        } else {
            Log.w(LOG_TAG, "Downloaded file isn't a newer version of this app")
            false
        }
    } catch (e: IOException) {
        Log.w(LOG_TAG, "Update download failed", e)
        false
    }

    // Android itself refuses an APK signed with another key; this also rejects anything that isn't a newer
    // version of this app before the user is asked.
    private fun isUpgradeOfThisApp(context: Context, apk: File): Boolean {
        val archive = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        return archive.packageName == context.packageName && archive.longVersionCode > installed.longVersionCode
    }

    private fun commit(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12+: once this app installed itself, later updates may skip the confirmation screen.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = Intent(context, UpdateInstallReceiver::class.java)
            // Mutable: the installer adds the outcome to this (explicit) intent.
            val pending = PendingIntent.getBroadcast(
                context, id, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    private fun get(url: String): String? {
        val connection = open(url)
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().readText() else null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    // GitHub serves release files through a redirect, which HttpURLConnection follows within https.
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "DumbCallerId")
    }
}
