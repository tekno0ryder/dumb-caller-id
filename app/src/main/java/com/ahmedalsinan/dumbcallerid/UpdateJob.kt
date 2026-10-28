package com.ahmedalsinan.dumbcallerid

import android.app.job.JobParameters
import android.app.job.JobService

// The daily update check scheduled by Updates.schedule. Runs off the main thread, as it uses the network.
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            Updates.check(this)
            jobFinished(params, false)
        }.start()
        return true // still working, on the thread above
    }

    override fun onStopJob(params: JobParameters): Boolean = true // retry later
}
