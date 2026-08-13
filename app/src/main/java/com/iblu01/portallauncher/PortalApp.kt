package com.iblu01.portallauncher

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.iblu01.portallauncher.photo.FilePhotoCache
import com.iblu01.portallauncher.photo.PhotoCoordinator
import com.iblu01.portallauncher.photo.createDefaultPhotoCoordinator
import com.iblu01.portallauncher.photo.DefaultPhotoSourceProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Hilt entry point: hosts the app-wide dependency graph (SingletonComponent). */
@HiltAndroidApp
class PortalApp : Application(), ImageLoaderFactory {

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

    /**
     * The single process-wide Coil loader, built lazily on the first image request. Every
     * `AsyncImage` without an explicit loader uses it, so caches survive panel navigation instead
     * of dying with the composable that built them.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }   // bundled Meteocons assets are SVG
        // Explicit budgets: Coil's 25% default is untenable on an 80 MB heap growth limit.
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(32L * 1024 * 1024)
                .build()
        }
        .allowRgb565(true)   // 2 bytes/px instead of 4; invisible on covers and weather glyphs
        .okHttpClient {
            // Local HA traffic must bypass the tablet's HTTP proxy, like HaStateRepository does.
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
        .crossfade(200)   // a 1 s fade is a stutter, not a fade, at 122 ms per frame
        .build()
}
