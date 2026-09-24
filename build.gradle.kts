import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val minSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("minSdkMajor").map { it.toInt() }

private val targetSdkMajorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMajor").map { it.toInt() }

private val targetSdkMinorProperty: Provider<Int> =
    providers.gradleProperty("targetSdkMinor").map { it.toInt() }

plugins {
    id("maven-publish")
    alias(libs.plugins.android.library)
    alias(libs.plugins.exterastuff.plugin)
}

group = "ru.n08i40k"
version = "1.2.1"

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

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        all {
            buildConfigField("String", "BUILD_VERSION", "\"${project.version}\"")
        }

        debug {
            buildConfigField("long", "BUILD_TIME", "0")
        }

        release {
            buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}")

            isMinifyEnabled = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    explicitApi()

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
    compileOnly(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "ru.n08i40k"
            artifactId = "badges-sdk"
            version = "1.2.1"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

extera {
    telegram {
        jar = file("libs/Telegram.jar")

        conflictingPackages = listOf("kotlin", "kotlinx")
    }

    // not used as project will be published as AAR

    r8 {
        minSdk = minSdkMajorProperty.get()
        proguardFiles = files("proguard-rules.pro")
    }

    shadow {
        targetPackage = "ru.n08i40k.badges_shaded"

        relocate("kotlin", "kotlinx")
    }
}
