plugins {
    alias(libs.plugins.android.library)
}

group = "ru.n08i40k"
version = "1.2.0"

android {
    namespace = "ru.n08i40k.badges.api"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        enableKotlin = false
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

dependencies {
    compileOnly(libs.jetbrains.annotations)
}
