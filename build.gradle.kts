// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.quarkus) apply false
    alias(libs.plugins.quarkus.extension) apply false
}
