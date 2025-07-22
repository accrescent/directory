// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.events.v1.AppPublicationRequested
import app.accrescent.events.v1.ReleaseChannel
import app.accrescent.events.v1.copy
import app.accrescent.events.v1.objectMetadata
import com.google.protobuf.TextFormat
import java.util.stream.Stream

object TestDataHelper {
    val validAppPublicationRequested: AppPublicationRequested = TestDataHelper::class.java
        .classLoader
        .getResourceAsStream("valid-app-publication-requested.txtpb")!!
        .use {
            val builder = AppPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()
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

    @JvmStatic
    fun generateInvalidAppPublicationRequested(): Stream<AppPublicationRequested> = Stream.of(
        // Missing the app ID
        validAppPublicationRequested.copy { app = app.copy { clearAppId() } },
        // Missing the app metadata
        validAppPublicationRequested.copy { clearApp() },
        // Missing the default listing language
        validAppPublicationRequested.copy {
            app = app.copy { clearDefaultListingLanguage() }
        },
        // Listing list doesn't contain the default listing language
        validAppPublicationRequested.toBuilder()
            .apply {
                appBuilder.removeListings(app.listingsList.indexOfFirst {
                    it.language == app.defaultListingLanguage
                })
            }
            .build(),
        // Listing contains duplicate languages
        validAppPublicationRequested.toBuilder()
            .apply { appBuilder.addListings(validAppPublicationRequested.app.listingsList[0]) }
            .build(),
        // Listings don't have a language set
        validAppPublicationRequested.toBuilder()
            .apply { appBuilder.listingsBuilderList.forEach { it.clearLanguage() } }
            .build(),
        // Listings don't have a name set
        validAppPublicationRequested.toBuilder()
            .apply { appBuilder.listingsBuilderList.forEach { it.clearName() } }
            .build(),
        // Listings don't have a short description set
        validAppPublicationRequested.toBuilder()
            .apply { appBuilder.listingsBuilderList.forEach { it.clearShortDescription() } }
            .build(),
        // Listings don't have an icon set
        validAppPublicationRequested.toBuilder()
            .apply { appBuilder.listingsBuilderList.forEach { it.clearIcon() } }
            .build(),
        // Listing icons don't have an object ID set
        validAppPublicationRequested.toBuilder()
            .apply {
                appBuilder.listingsBuilderList.forEach { it.iconBuilder.clearObjectId() }
            }
            .build(),
        // Package metadata doesn't exist for the stable release channel
        validAppPublicationRequested.toBuilder()
            .apply {
                appBuilder.removePackageMetadata(app.packageMetadataList.indexOfFirst {
                    it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
                })
            }
            .build(),
        // Package metadata contains duplicate release channels
        validAppPublicationRequested.toBuilder()
            .apply {
                appBuilder
                    .addPackageMetadata(validAppPublicationRequested.app.packageMetadataList[0])
            }
            .build(),
        // Package metadata is missing metadata for an object
        validAppPublicationRequested.toBuilder()
            .apply {
                appBuilder.packageMetadataBuilderList[0].packageMetadataBuilder
                    .removeObjectMetadata("38119a8c-1163-4c7d-89c6-cc5c902a6ca1")
            }
            .build(),
        // An object's metadata doesn't have uncompressed size set
        validAppPublicationRequested.toBuilder()
            .apply {
                val packageMetadataBuilder = appBuilder
                    .packageMetadataBuilderList[0]
                    .packageMetadataBuilder
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
        validAppPublicationRequested.toBuilder()
            .apply {
                appBuilder.packageMetadataBuilderList[0].packageMetadataBuilder
                    .putObjectMetadata(
                        "nonexistent-object",
                        objectMetadata { uncompressedSize = 4096 }
                    )
            }
            .build(),
    )
}
