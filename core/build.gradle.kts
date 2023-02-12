plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.core"
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    buildTypes {
        create("benchmark") {
        }
        create("releaseTest") {
        }
    }
}

dependencies {
    implementation(project(":i18n"))

    api(libs.logcat)

    api(libs.rxjava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.dnsoverhttps)
    api("com.squareup.okio:okio:3.3.0")

    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    api(libs.injekt.core)

    api(libs.preferencektx)

    implementation(androidx.corektx)

    // JavaScript engine
    implementation(libs.bundles.js.engine)

    // SY -->
    implementation(sylibs.xlog)
    // SY <--
}
