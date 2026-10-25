plugins {
    id("com.android.application")
}

val appVersionName: String = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "0.3.0-beta"
val appVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 30

android {
    namespace = "org.pooltool.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.pooltool.android"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
