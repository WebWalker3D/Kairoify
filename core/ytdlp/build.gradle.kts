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
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "icu.kairo.kairoify.core.ytdlp"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
}

kotlin { jvmToolchain(21) }

chaquopy {
    defaultConfig {
        version = "3.14"
        pip { install("-r", "requirements.txt") }
    }
}

/** Fetches the QuickJS binary yt-dlp uses for YouTube's JavaScript challenges, checking each against its pinned SHA-256. */
abstract class DownloadQuickJs : DefaultTask() {
    /** ABI → "url sha256". */
    @get:Input
    abstract val binaries: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        binaries.get().forEach { (abi, spec) ->
            val (url, expected) = spec.split(" ")
            val bytes = URI(url).toURL().openStream().use { it.readBytes() }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == expected) { "QuickJS for $abi has SHA-256 $actual, expected $expected" }
            outputDir.file("$abi/libqjs.so").get().asFile.apply {
                parentFile.mkdirs()
                writeBytes(bytes)
            }
        }
    }
}

val downloadQuickJs = tasks.register<DownloadQuickJs>("downloadQuickJs") {
    binaries.put(
        "arm64-v8a",
        "https://github.com/quickjs-ng/quickjs/releases/download/v0.18.0/qjs-linux-aarch64 " +
            "2c005c7171424a598d31c53c8dc4a0ae2e4958eb6fbb37908c885a307c78187c",
    )
    binaries.put(
        "x86_64",
        "https://github.com/quickjs-ng/quickjs/releases/download/v0.18.0/qjs-linux-x86_64 " +
            "85ded23b9fabe8c1644fc40a8d2bbdd71157affcbd807359c618cc717ee24d7d",
    )
    outputDir.set(layout.buildDirectory.dir("generated/quickjs/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(downloadQuickJs, DownloadQuickJs::outputDir)
    }
}

/**
 * Every Python package that ships in the app, with the license a person checked. A package missing from this map
 * fails the build: Kairoify is CPAL-licensed, so nothing GPL/AGPL may slip in through pip.
 */
val reviewedPythonLicenses = mapOf(
    "yt_dlp" to "Unlicense",
    "yt_dlp_ejs" to "Unlicense AND MIT AND ISC",
    "ytmusicapi" to "MIT",
    "requests" to "Apache-2.0",
    "urllib3" to "MIT",
    "idna" to "BSD-3-Clause",
    "charset_normalizer" to "MIT",
    "certifi" to "MPL-2.0",
)

val checkPythonLicenses = tasks.register("checkPythonLicenses") {
    description = "Fails when a bundled Python package has no reviewed license."
    group = "verification"
    dependsOn("installDebugPythonRequirements")
    val pipDir = layout.buildDirectory.dir("python/pip/debug/common")
    val reviewed = reviewedPythonLicenses.keys
    inputs.dir(pipDir)
    doLast {
        val dists = pipDir.get().asFile.listFiles { f -> f.name.endsWith(".dist-info") }.orEmpty()
        check(dists.isNotEmpty()) { "No Python packages found in ${pipDir.get().asFile}" }
        val unknown = dists.map { it.name.substringBefore('-').lowercase().replace('-', '_') }.filter { it !in reviewed }.sorted()
        if (unknown.isNotEmpty()) {
            throw GradleException(
                unknown.joinToString("\n") {
                    "Python package $it has no reviewed license. Check it is not GPL/AGPL, then add it to reviewedPythonLicenses."
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkPythonLicenses) }

dependencies {
    api(project(":core:source-api"))
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
