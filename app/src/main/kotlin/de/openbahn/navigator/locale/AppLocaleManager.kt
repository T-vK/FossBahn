package de.openbahn.navigator.locale

import androidx.appcompat.app.AppCompatDelegate

object AppLocaleManager {
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(language.toLocaleList())
    }
}
