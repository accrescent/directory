// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "directory"

include("bundletool-relocated", "directory")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven {
            name = "buf"
            url = uri("https://buf.build/gen/maven")
        }
        maven {
            name = "confluent"
            url = uri("https://packages.confluent.io/maven")
        }
    }
}
