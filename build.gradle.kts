import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val minSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("minSdkMajor").map { it.toInt() }

private val targetSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMajor").map { it.toInt() }

private val targetSdkMinorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMinor").map { it.toInt() }

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.exterastuff.plugin)
}

android {
    namespace = "ru.n08i40k.badges"

    buildFeatures {
        buildConfig = true
    }

    compileSdk {
        version = release(targetSdkMajorProperty.get()) {
            minorApiLevel = targetSdkMinorProperty.get()
        }
    }

    defaultConfig {
        minSdk = minSdkMajorProperty.get()

        lint {
            targetSdk = targetSdkMajorProperty.get()
        }
    }

    buildTypes {
        debug {
            buildConfigField("long", "BUILD_TIME", "0")
        }

        release {
            buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xmetadata-version=2.2.0")
        freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":api"))

    compileOnly(libs.aliuhook)

    implementation(libs.jetbrains.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    compileOnly(libs.androidx.recyclerview)
    compileOnly(libs.androidx.lifecycle.viewmodel)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

extera {
    telegram {
        jar = file("libs/Telegram.jar")

        conflictingPackages = listOf(
            "kotlin",
            "kotlinx",
            "androidx.annotation",
            "androidx.arch",
            "androidx.collection",
            "androidx.core",
            "androidx.customview",
            "androidx.lifecycle",
            "androidx.recyclerview",
            "androidx.versionedparcelable",
        )
    }

    r8 {
        minSdk = minSdkMajorProperty.get()
        proguardFiles = files("proguard-rules.pro")
    }

    shadow {
        targetPackage = "ru.n08i40k.badges_shaded"

        relocate("kotlin", "kotlinx")
    }

    dexOutputDir = project.layout.projectDirectory.dir("dist/dex")
}
