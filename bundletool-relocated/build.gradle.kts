// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

dependencies {
    runtimeOnly(libs.bundletool)
}

tasks.named<ShadowJar>("shadowJar") {
    dependencies {
        // Type-safe projects accessors (i.e. "libs.bundletool") are not supported until shadow
        // 9.0.0-rc1, so work around this limitation for now.
        include(dependency(libs.bundletool.map { "${it.group}:${it.name}:${it.version}" }.get()))
    }
    relocate("com.android.bundle", "app.accrescent.bundletool.android.bundle")
}
