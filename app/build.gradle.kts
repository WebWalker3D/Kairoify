/*
 * Copyright (c) 2026 WebWalker3D. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public Attribution License
 * Version 1.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * https://github.com/WebWalker3D/Kairoify/blob/main/LICENSE. The License is based on
 * the Mozilla Public License Version 1.1 but Sections 14 and 15 have been added to
 * cover use of software over a computer network and provide for limited attribution
 * for the Original Developer. In addition, Exhibit A has been modified to be
 * consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT
 * WARRANTY OF ANY KIND, either express or implied. See the License for the specific
 * language governing rights and limitations under the License.
 *
 * The Original Code is Kairoify. The Initial Developer of the Original Code is
 * WebWalker3D. Attribution information: see NOTICE.md.
 */
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.licensee)
}

android {
    namespace = "icu.kairo.kairoify"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "icu.kairo.kairoify"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"

        // Jamendo needs a registered client ID. It never goes in the repo: pass -Pkairoify.jamendo.clientId=... or set
        // KAIROIFY_JAMENDO_CLIENT_ID. Without one, the Jamendo source is left out of the app.
        val jamendoClientId = (findProperty("kairoify.jamendo.clientId") as String?) ?: System.getenv("KAIROIFY_JAMENDO_CLIENT_ID") ?: ""
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${jamendoClientId.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    // QuickJS has to exist as a real file to be executed, so native libraries are extracted at install.
    packaging { jniLibs { useLegacyPackaging = true } }

    // Release signing comes from a properties file kept outside the repo (storeFile, storePassword, keyAlias,
    // keyPassword). Point to it with -Pkairoify.signing=/path or KAIROIFY_SIGNING; without it, release builds are unsigned.
    val signingFile = (findProperty("kairoify.signing") as String?) ?: System.getenv("KAIROIFY_SIGNING")
    val signing = signingFile?.let { path ->
        Properties().apply { file(path).inputStream().use { load(it) } }
    }
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            if (signing != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    // One APK per CPU type keeps downloads small: arm64-v8a for phones, x86_64 for emulators. These are the only ABIs
    // built at all: the embedded Python (3.14) and QuickJS are 64-bit only.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

kotlin { jvmToolchain(21) }

licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("ISC")
    allow("Unlicense")
    allow("MPL-2.0")
}

dependencies {
    implementation(project(":core:player"))
    implementation(project(":core:data"))
    implementation(project(":core:ytdlp"))
    implementation(project(":sources:ytdlp"))
    implementation(project(":sources:archive"))
    implementation(project(":sources:audius"))
    implementation(project(":sources:jamendo"))
    implementation(project(":sources:hearthis"))
    implementation(project(":sources:ccmixter"))
    implementation(project(":sources:wikimedia"))
    implementation(project(":sources:radiobrowser"))
    implementation(project(":sources:openverse"))
    implementation(project(":sources:somafm"))
    implementation(project(":sources:loc"))
    implementation(project(":sources:freesound"))
    implementation(project(":sources:europeana"))
    implementation(project(":sources:podcasts"))
    implementation(project(":sources:wavlake"))
    implementation(project(":sources:radioparadise"))
    implementation(project(":sources:listenbrainz"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
