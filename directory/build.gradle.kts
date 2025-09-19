// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import build.buf.gradle.BUF_BINARY_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.buf)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.quarkus)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom)) {
        // Quarkus is still using protobuf-java v3, but we need v4 of the protobuf runtime for
        // protovalidate. Otherwise, instantiating a protovalidate Validator results in
        // "java.lang.NoClassDefFoundError: com/google/protobuf/RuntimeVersion$RuntimeDomain".
        //
        // protobuf-java v3 gencode is compatible with the v4 runtime according to
        // https://github.com/protocolbuffers/protobuf/issues/16452#issuecomment-2310384628, so
        // it should be safe for us to exclude Quarkus's protobuf from platform enforcement and so
        // be able to use protobuf-java v4 as pulled in by protovalidate.
        //
        // Once Quarkus updates to protobuf-java v4, we can remove this workaround. See
        // https://github.com/quarkusio/quarkus/issues/44681 and
        // https://github.com/quarkusio/quarkus/pull/47157 for progress on that.
        exclude("com.google.protobuf", "protobuf-java")
    }
    implementation(project(":bundletool-relocated", "shadow"))
    implementation(libs.flyway.postgresql)
    implementation(libs.managed.kafka.auth.login.handler)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protovalidate)
    implementation(libs.quarkus.agroal)
    implementation(libs.quarkus.flyway)
    implementation(libs.quarkus.hibernate.reactive)
    implementation(libs.quarkus.hibernate.reactive.panache)
    implementation(libs.quarkus.hibernate.reactive.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.kotlin)
    implementation(libs.quarkus.messaging.kafka)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.grpc)
    implementation(libs.quarkus.reactive.pg)
    implementation(libs.server.events)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.test.hibernate.reactive.panache)
    testImplementation(libs.quarkus.test.kafka.companion)
    testImplementation(libs.quarkus.test.vertx)
}

group = "app.accrescent.server"
version = "0.1.0-rc.3"

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        nativeImageCapable = true
    }

    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
        javaParameters = true

        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

// We need the Buf Gradle plugin only for the Buf CLI binary and Gradle emits errors regarding task
// dependencies including these tasks, so we disable them since they're unneeded and cause errors
// when enabled.
tasks.bufFormatCheck {
    enabled = false
}
tasks.bufLint {
    enabled = false
}

tasks.register<Exec>("downloadDirectoryApiProtos") {
    inputs.property("app.accrescent.directory.directory-api-version", libs.versions.directory.api)
    outputs.dir("$projectDir/src/main/proto/accrescent/directory/v1")

    val bufExecutable = configurations.getByName(BUF_BINARY_CONFIGURATION_NAME).singleFile
    if (!bufExecutable.canExecute()) {
        bufExecutable.setExecutable(true)
    }

    val directoryApiVersion = inputs.properties["app.accrescent.directory.directory-api-version"]

    commandLine(
        bufExecutable.absolutePath,
        "export",
        "buf.build/accrescent/directory-api:$directoryApiVersion",
        "--output",
        "$projectDir/src/main/proto/",
    )

    // Remove buf/validate/validate.proto so that Quarkus doesn't generate classes which conflict
    // with those defined in our protovalidate dependency
    doLast {
        file("$projectDir/src/main/proto/buf").deleteRecursively()
    }
}
tasks.register("downloadProtos") {
    dependsOn(tasks.getByName("downloadDirectoryApiProtos"))
}
tasks.quarkusGenerateCode {
    dependsOn(tasks.getByName("downloadProtos"))
}

tasks.clean {
    delete("$projectDir/src/main/proto/accrescent/directory/v1")
    delete("$projectDir/src/main/proto/android")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

dokka {
    dokkaPublications.configureEach {
        failOnWarning = true
    }

    dokkaSourceSets.configureEach {
        reportUndocumented = true

        perPackageOption {
            matchingRegex =
                """^app\.accrescent\.directory\.((priv\.)?v1)|com\.(android\.bundle|google\.protobuf)"""
            suppress = true
        }
    }
}
