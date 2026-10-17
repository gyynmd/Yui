val projectName = "MBGA"
val androidCompileSdk = 34
val androidMinSdk = 27
val androidTargetSdk = 34
val appPackageName = "top.trangle.mbga"
val appVersionName = "1.5.0"
val appVersionCode = (System.getenv("BUILD_ID")?.toIntOrNull() ?: 170)

// libxposed API 102, compiled against but provided by the Xposed framework at runtime.
val libxposedApiVersion = "102.0.0"

plugins {
    id("com.android.application") version "8.6.1"
    id("org.jetbrains.kotlin.android") version "2.0.0"
}

android {
    namespace = appPackageName
    compileSdk = androidCompileSdk
    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x51"
    )

    defaultConfig {
        applicationId = appPackageName
        minSdk = androidMinSdk
        targetSdk = androidTargetSdk
        versionName = appVersionName
        versionCode = appVersionCode
    }

    val keystoreFile = rootProject.file("keystore/mbga-release.jks")
    if (keystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD") ?: "mbga123456"
                keyAlias = System.getenv("KEY_ALIAS") ?: "mbga"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "mbga123456"
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "_debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        all {
            var name = "$projectName ${defaultConfig.versionName}"
            if (versionNameSuffix != null) {
                name += versionNameSuffix
            }
            resValue("string", "app_name_with_version", name)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions"
        )
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Modern Xposed API (API 102). The framework injects the implementation at runtime.
    compileOnly("io.github.libxposed:api:$libxposedApiVersion")
    // Framework service used by the module app to publish its settings (remote preferences).
    implementation("io.github.libxposed:service:$libxposedApiVersion")

    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.hendraanggrian.material:collapsingtoolbarlayout-subtitle:1.5.0")
}
