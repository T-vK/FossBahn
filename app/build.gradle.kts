import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val appVersionName = versionProps.getProperty("versionName", "0.1.0")
val appVersionCode = versionProps.getProperty("versionCode", "1").toInt()

val ciKeystore = rootProject.file(".github/signing/openbahn-ci.jks")
val ciKeystoreProps = rootProject.file(".github/signing/ci.properties")
val useCiSigning = ciKeystore.exists() && ciKeystoreProps.exists() &&
    (System.getenv("CI") == "true" || project.hasProperty("useCiSigning"))

android {
    namespace = "de.openbahn.navigator"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.openbahn.navigator"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "de.openbahn.navigator.OpenBahnTestRunner"
        vectorDrawables { useSupportLibrary = true }
        val bahnVorhersageApiUrl = (project.findProperty("bahnVorhersageApiUrl") as String?)?.trim().orEmpty()
        buildConfigField("String", "BAHN_VORHERSAGE_API_URL", "\"$bahnVorhersageApiUrl\"")
    }

    signingConfigs {
        if (useCiSigning) {
            val ciProps = Properties().apply { ciKeystoreProps.inputStream().use { load(it) } }
            create("ci") {
                storeFile = ciKeystore
                storePassword = ciProps.getProperty("storePassword")
                keyAlias = ciProps.getProperty("keyAlias")
                keyPassword = ciProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (useCiSigning) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            if (useCiSigning) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.useJUnitPlatform() }
    }
}

@Suppress("DEPRECATION")
android.applicationVariants.configureEach {
    val vn = versionName
    val vc = versionCode
    val variantName = name
    outputs.configureEach {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "OpenBahnNavigator-v${vn}-${vc}-${variantName}.apk"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:api"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.documentfile)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.coil.compose)
    implementation(libs.osmdroid)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.jupiter.engine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":core:api"))
    androidTestImplementation(libs.koin.android)
    androidTestImplementation(libs.koin.compose)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
