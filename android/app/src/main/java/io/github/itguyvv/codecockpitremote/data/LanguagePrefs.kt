package io.github.itguyvv.codecockpitremote.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList

/**
 * In-app language choice. Stored in plain (synchronous) SharedPreferences because
 * attachBaseContext wraps the context before any async storage could possibly load.
 * Empty string = follow the system locale. Tags match the values-<qualifier> folders.
 */
object LanguagePrefs {
    private const val FILE = "app_prefs"
    private const val KEY = "language"

    /** BCP-47 tag → native display name; names stay in their own language by design */
    val choices: List<Pair<String, String>> = listOf(
        "" to "", // system default — the UI shows a translated label instead
        "en" to "English",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский"
    )

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Rebuild the context so resource lookups resolve against the chosen locale */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(config)
    }
}
