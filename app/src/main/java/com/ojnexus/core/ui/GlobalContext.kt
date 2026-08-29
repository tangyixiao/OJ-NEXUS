package com.ojnexus.core.ui

import android.app.Application

/**
 * Application context holder for non-UI subsystems that need to enqueue WorkManager jobs
 * without a Composable context (e.g. the Settings ViewModel). Set once in
 * [com.ojnexus.OjNexusApplication.onCreate].
 */
object GlobalContext {
    lateinit var application: Application
        private set

    fun init(application: Application) {
        this.application = application
    }
}
