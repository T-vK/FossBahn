package de.openbahn.navigator.locale

import android.content.Context
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val storageValue: String) {
    SYSTEM("system"),
    GERMAN("de"),
    ENGLISH("en"),
    ;

    fun toLocaleList(): LocaleListCompat = when (this) {
        SYSTEM -> LocaleListCompat.getEmptyLocaleList()
        GERMAN -> LocaleListCompat.forLanguageTags("de")
        ENGLISH -> LocaleListCompat.forLanguageTags("en")
    }

    /** Locale tag sent to bahn.de (`de` or `en`). */
    fun apiLocale(context: Context): String = when (this) {
        GERMAN -> "de"
        ENGLISH -> "en"
        SYSTEM -> {
            val device = context.resources.configuration.locales.get(0)?.language?.lowercase()
            if (device?.startsWith("de") == true) "de" else "en"
        }
    }

    companion object {
        fun fromStorage(value: String?): AppLanguage =
            entries.find { it.storageValue == value } ?: SYSTEM
    }
}
