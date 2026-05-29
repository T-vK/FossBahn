package de.openbahn.navigator

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner for live API UI tests (real [OpenBahnApplication], no fake repository).
 *
 * Run manually:
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunner=de.openbahn.navigator.OpenBahnLiveTestRunner \
 *   -Pandroid.testInstrumentationRunnerArguments.runLiveSearchE2e=true
 */
class OpenBahnLiveTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, OpenBahnApplication::class.java.name, context)
}
