package com.iblu01.portallauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import com.iblu01.portallauncher.ui.onboarding.OnboardingLauncherPreview
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Transient browser preview rendered by the real launcher. Nothing here is persisted. */
object LauncherWebPreview {
    private val mutablePreview = MutableStateFlow<OnboardingLauncherPreview?>(null)
    val preview = mutablePreview.asStateFlow()

    @Volatile private var launcherView = WeakReference<View>(null)

    fun apply(value: OnboardingLauncherPreview?) {
        mutablePreview.value = value
    }

    fun register(view: View) {
        launcherView = WeakReference(view)
    }

    fun unregister(view: View) {
        if (launcherView.get() === view) launcherView.clear()
    }

    fun capturePng(): ByteArray? {
        var result: ByteArray? = null
        val finished = CountDownLatch(1)
        val capture = Runnable {
            runCatching {
                val view = launcherView.get() ?: return@runCatching
                if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return@runCatching
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                result = ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
                bitmap.recycle()
            }
            finished.countDown()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) capture.run() else {
            Handler(Looper.getMainLooper()).post(capture)
            finished.await(1, TimeUnit.SECONDS)
        }
        return result
    }
}
