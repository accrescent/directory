// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

rootProject.name = "directory"

include(
    "bundletool-relocated",
    "directory",
    "quarkus-devservices-clickhouse",
    "quarkus-jdbc-clickhouse",
    "quarkus-jdbc-clickhouse-deployment",
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}
