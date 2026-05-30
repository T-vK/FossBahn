plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.openbahn.api"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.jupiter.engine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

/** Offline + optional live verification for ICE 603 / FLX 1247 delay scenarios. */
afterEvaluate {
    tasks.register<Test>("verifyDelayScenarios") {
        description = "Verify Verspätung/cancellation for ICE 603 and FLX 1247 (2026-05-30 scenarios)"
        group = "verification"
        val unitTest = tasks.named<Test>("testDebugUnitTest").get()
        testClassesDirs = unitTest.testClassesDirs
        classpath = unitTest.classpath
        useJUnitPlatform {
            includeTags("delay-scenario")
        }
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
        }
    }
}
