// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.server.events.v1.App
import app.accrescent.server.events.v1.ReleaseChannel

/**
 * Validates the fields of an [App] object.
 *
 * @throws IllegalArgumentException if the [app] passed is invalid.
 */
fun validateApp(app: App): Unit = when {
    !app.hasAppId() -> throw IllegalArgumentException("app ID is missing but required")

    !app.hasDefaultListingLanguage() ->
        throw IllegalArgumentException("default listing language is missing but required")

    app.listingsList.map { it.language }.distinct().count() != app.listingsCount ->
        throw IllegalArgumentException("listing languages must not be duplicated")

    app.listingsCount < 1 ->
        throw IllegalArgumentException("no listings found but at least one required")

    !app.listingsList.any { it.language == app.defaultListingLanguage } ->
        throw IllegalArgumentException("no listing found for default listing language")

    !app.listingsList.all { it.hasLanguage() } ->
        throw IllegalArgumentException("all listings must specify a language")

    !app.listingsList.all { it.hasName() } ->
        throw IllegalArgumentException("all listings must specify a name")

    !app.listingsList.all { it.hasShortDescription() } ->
        throw IllegalArgumentException("all listings must specify a short description")

    !app.listingsList.all { it.hasIcon() } ->
        throw IllegalArgumentException("all listings must specify an icon")

    !app.listingsList.all { it.icon.hasObjectId() } ->
        throw IllegalArgumentException("all listings icons must specify an object ID")

    !app.packageMetadataList.any {
        it.releaseChannel.hasWellKnown() &&
                it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
    } -> throw IllegalArgumentException("stable channel metadata must be provided")

    app.packageMetadataList.map { it.releaseChannel }
        .distinct()
        .count() != app.packageMetadataCount
        -> throw IllegalArgumentException("release channels must not be duplicated")

    !app.packageMetadataList.map { it.packageMetadata }
        .all { packageMetadata ->
            packageMetadata.buildApksResult.variantList.flatMap { it.apkSetList }
                .flatMap { it.apkDescriptionList }
                .map { it.path }
                .all { packageMetadata.objectMetadataMap.contains(it) }
        } -> throw IllegalArgumentException("all objects must have metadata specified")

    !app.packageMetadataList.map { it.packageMetadata }
        .all { packageMetadata ->
            packageMetadata.buildApksResult.variantList.flatMap { it.apkSetList }
                .flatMap { it.apkDescriptionList }
                .map { it.path }
                .all { packageMetadata.objectMetadataMap[it]?.hasUncompressedSize() == true }
        } ->
        throw IllegalArgumentException("all objects must have an uncompressed size specified")

    !app.packageMetadataList.map { it.packageMetadata }
        .all { packageMetadata ->
            val buildApksResultObjectIds = packageMetadata.buildApksResult.variantList
                .flatMap { it.apkSetList }
                .flatMap { it.apkDescriptionList }
                .map { it.path }
                .toSet()
            packageMetadata.objectMetadataMap.keys.all { buildApksResultObjectIds.contains(it) }
        } -> throw IllegalArgumentException("object metadata found for unspecified object")

    else -> Unit
}
