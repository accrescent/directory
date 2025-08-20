// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

dependencies {
    runtimeOnly(libs.bundletool)
}

tasks.shadowJar {
    dependencies {
        include(dependency(libs.bundletool))
    }
    relocate("com.android.bundle", "app.accrescent.bundletool.android.bundle")
}
