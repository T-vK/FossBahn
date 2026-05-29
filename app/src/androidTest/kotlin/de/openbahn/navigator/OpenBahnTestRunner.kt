package de.openbahn.navigator

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class OpenBahnTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, OpenBahnApplication::class.java.name, context)
    }
}
