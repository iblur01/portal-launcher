package com.iblu01.portallauncher

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps a base [Context] with the language chosen in Settings > Application > Language, or
 * returns it unchanged when the user follows the system language. Every Activity (and the
 * Application) must call this from `attachBaseContext` — plain `ComponentActivity`s don't get
 * per-app locale overrides for free the way `AppCompatActivity` does.
 */
object LocaleHelper {
    fun wrap(context: Context): Context {
        // Read the raw pref directly instead of going through Prefs(context): during
        // Application.attachBaseContext, context.applicationContext is still null, and Prefs
        // dereferences it immediately — that crashed the app on every launch.
        val sp = context.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE)
        val code = sp.getString("app_language", "") ?: ""
        if (code.isBlank()) return context
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
