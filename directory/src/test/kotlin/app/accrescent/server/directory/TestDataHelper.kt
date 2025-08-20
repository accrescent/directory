// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import build.buf.gen.accrescent.server.events.v1.App
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import build.buf.gen.accrescent.server.events.v1.ReleaseChannel
import build.buf.gen.accrescent.server.events.v1.appPublicationRequested
import build.buf.gen.accrescent.server.events.v1.copy
import build.buf.gen.accrescent.server.events.v1.objectMetadata
import com.google.protobuf.TextFormat
import java.util.stream.Stream

object TestDataHelper {
    val validApp: App = TestDataHelper::class.java.classLoader
        .getResourceAsStream("valid-app.txtpb")!!
        .use {
            val builder = App.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()

    val validAppEditPublicationRequested: AppEditPublicationRequested = TestDataHelper::class.java
        .classLoader
        .getResourceAsStream("valid-app-edit-publication-requested.txtpb")!!
        .use {
            val builder = AppEditPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()

    val validAppPublicationRequested: AppPublicationRequested = appPublicationRequested {
        app = validApp
    }
    val validAppPublicationRequested2: AppPublicationRequested = TestDataHelper::class.java
        .classLoader
        .getResourceAsStream("valid-app-publication-requested-2.txtpb")!!
        .use {
            val builder = AppPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()
    val validAppPublicationRequested3Incompatible: AppPublicationRequested = TestDataHelper::class
        .java
        .classLoader
        .getResourceAsStream("valid-app-publication-requested-3-incompatible.txtpb")!!
        .use {
            val builder = AppPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()

    val invalidAppEditPublicationRequested = validAppEditPublicationRequested.copy { clearEdit() }
    val invalidAppPublicationRequested = validAppPublicationRequested.copy { clearApp() }

    @JvmStatic
    fun generateInvalidApp(): Stream<App> = Stream.of(
        // Missing the app ID
        validApp.copy { clearAppId() },
        // Missing the default listing language
        validApp.copy { clearDefaultListingLanguage() },
        // Listing list doesn't contain the default listing language
        validApp.toBuilder().removeListings(validApp.listingsList.indexOfFirst {
            it.language == validApp.defaultListingLanguage
        }).build(),
        // Listing contains duplicate languages
        validApp.toBuilder().addListings(validApp.listingsList[0]).build(),
        // Listings don't have a language set
        validApp.toBuilder().apply { listingsBuilderList.forEach { it.clearLanguage() } }.build(),
        // Listings don't have a name set
        validApp.toBuilder().apply { listingsBuilderList.forEach { it.clearName() } }.build(),
        // Listings don't have a short description set
        validApp.toBuilder().apply { listingsBuilderList.forEach { it.clearShortDescription() } }.build(),
        // Listings don't have an icon set
        validApp.toBuilder().apply { listingsBuilderList.forEach { it.clearIcon() } }.build(),
        // Listing icons don't have an object ID set
        validApp.toBuilder().apply { listingsBuilderList.forEach { it.iconBuilder.clearObjectId() } }.build(),
        // Package metadata doesn't exist for the stable release channel
        validApp.toBuilder().removePackageMetadata(validApp.packageMetadataList.indexOfFirst {
            it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
        }).build(),
        // Package metadata contains duplicate release channels
        validApp.toBuilder().addPackageMetadata(validApp.packageMetadataList[0]).build(),
        // Package metadata is missing metadata for an object
        validApp
            .toBuilder()
            .apply {
                packageMetadataBuilderList[0]
                    .packageMetadataBuilder
                    .removeObjectMetadata("38119a8c-1163-4c7d-89c6-cc5c902a6ca1")
            }
            .build(),
        // An object's metadata doesn't have uncompressed size set
        validApp
            .toBuilder()
            .apply {
                val packageMetadataBuilder = packageMetadataBuilderList[0].packageMetadataBuilder
                packageMetadataBuilder.putObjectMetadata(
                    "38119a8c-1163-4c7d-89c6-cc5c902a6ca1",
                    packageMetadataBuilder
                        .objectMetadataMap["38119a8c-1163-4c7d-89c6-cc5c902a6ca1"]!!
                        .toBuilder()
                        .clearUncompressedSize()
                        .build()
                )
            }
            .build(),
        // Object metadata is specified for an object not found in the build-apks result
        validApp
            .toBuilder()
            .apply {
                packageMetadataBuilderList[0]
                    .packageMetadataBuilder
                    .putObjectMetadata(
                        "nonexistent-object",
                        objectMetadata { uncompressedSize = 4096 },
                    )
            }
            .build(),
    )
}
