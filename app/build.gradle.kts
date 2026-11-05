plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace  = "com.anpaiss.batteryusage"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.anpaiss.batteryusage"
        minSdk        = 31
        targetSdk     = 34
        versionCode   = 1
        versionName   = "0.1"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
}
