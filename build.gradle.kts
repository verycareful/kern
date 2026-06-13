plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.kern"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kern"
        minSdk = 26
        targetSdk = 35
        // A.B.C.D versioning: D=2+ are real patches, one issue each (see release plan).
        // 0.1.8.1 adds tests for Excel persistence
        versionCode = 21
        versionName = "0.1.8.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // PDF engine: Kern loads libkern_pdf.so (built from the qyra/kern-bridge
        // crate via cargo-ndk, see src/pdf-bridge/README.md). Limit to the ABIs we build.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Feature-first source layout under src/, matching docs/architecture.md.
    // The app module is the root project, so manifest + res + kotlin all live at the root.
    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src")
        res.srcDirs("res")
        // Native PDF engine. The .so files are produced by the cargo-ndk build of
        // the qyra/kern-bridge crate (gitignored; see src/pdf-bridge/README.md),
        // dropped here as jniLibs/<abi>/libkern_pdf.so.
        jniLibs.srcDirs("jniLibs")
    }
    sourceSets["test"].java.srcDirs("tests")
    sourceSets["androidTest"].java.srcDirs("tests/instrumented")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        lintConfig = file("config/lint.xml")
    }
    packaging {
        resources {
            // Apache POI pulls in duplicate license/metadata files; exclude the noisy ones.
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Office formats (CSV/Excel/Word/PowerPoint). PDF arrives in 0.1.5.0 via the Qyra bridge.
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.opencsv)
    // EPUB (0.1.6.0): ZIP container parsed in-house, XHTML via Jsoup.
    implementation(libs.jsoup)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
