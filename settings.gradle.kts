// Copyright 2024-2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "directory"

include("directory")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}
