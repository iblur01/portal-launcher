package com.iblu01.portallauncher

import android.app.Application
import android.content.Context
import com.iblu01.portallauncher.photo.FilePhotoCache
import com.iblu01.portallauncher.photo.PhotoCoordinator
import com.iblu01.portallauncher.photo.createDefaultPhotoCoordinator
import com.iblu01.portallauncher.photo.DefaultPhotoSourceProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hilt entry point: hosts the app-wide dependency graph (SingletonComponent). */
@HiltAndroidApp
class PortalApp : Application() {

    lateinit var photoCoordinator: PhotoCoordinator
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        photoCoordinator = createDefaultPhotoCoordinator(
            provider = DefaultPhotoSourceProvider(prefs),
            cache = FilePhotoCache(this),
            scope = scope,
            prefs = prefs,
        )
        photoCoordinator.start()
    }
}
