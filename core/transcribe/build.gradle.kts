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
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "icu.kairo.kairoify.core.transcribe"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        if (System.getenv("CODEQL_EXTRACTOR_JAVA_ROOT") == null) externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release") } }
    }
    // CodeQL's automatic build (it scans Kotlin here, not C++) runs without an NDK, so it skips whisper.cpp.
    // Every other build compiles it, and fails loudly when the NDK is missing.
    if (System.getenv("CODEQL_EXTRACTOR_JAVA_ROOT") == null) externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":core:model"))
    api(project(":sources:lyrics"))
    api(project(":core:network"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
}
