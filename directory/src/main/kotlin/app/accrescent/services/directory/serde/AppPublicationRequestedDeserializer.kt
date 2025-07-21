// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.events.v1.AppPublicationRequested
import app.accrescent.events.v1.ReleaseChannel
import org.apache.kafka.common.serialization.Deserializer

/**
 * Kafka deserializer for [AppPublicationRequested] events.
 *
 * In addition to basic deserialization, this deserializer also performs validation on all fields of
 * the event. Thus, event consumers can safely assume the event has already been validated when they
 * receive it.
 */
class AppPublicationRequestedDeserializer : Deserializer<AppPublicationRequested> {
    /**
     * @suppress
     */
    override fun deserialize(topic: String, data: ByteArray): AppPublicationRequested {
        val message = AppPublicationRequested.parseFrom(data)

        validateEvent(message)

        return message
    }

    private companion object {
        private fun validateEvent(event: AppPublicationRequested): Unit = when {
            !event.hasApp() -> throw IllegalArgumentException("app metadata is missing but required")

            !event.app.hasAppId() -> throw IllegalArgumentException("app ID is missing but required")

            !event.app.hasDefaultListingLanguage() ->
                throw IllegalArgumentException("default listing language is missing but required")

            event.app.listingsList.map { it.language }.distinct().count() != event.app.listingsCount ->
                throw IllegalArgumentException("listing languages must not be duplicated")

            event.app.listingsCount < 1 ->
                throw IllegalArgumentException("no listings found but at least one required")

            !event.app.listingsList.any { it.language == event.app.defaultListingLanguage } ->
                throw IllegalArgumentException("no listing found for default listing language")

            !event.app.listingsList.all { it.hasLanguage() } ->
                throw IllegalArgumentException("all listings must specify a language")

            !event.app.listingsList.all { it.hasName() } ->
                throw IllegalArgumentException("all listings must specify a name")

            !event.app.listingsList.all { it.hasShortDescription() } ->
                throw IllegalArgumentException("all listings must specify a short description")

            !event.app.listingsList.all { it.hasIcon() } ->
                throw IllegalArgumentException("all listings must specify an icon")

            !event.app.listingsList.all { it.icon.hasObjectId() } ->
                throw IllegalArgumentException("all listings icons must specify an object ID")

            !event.app.packageMetadataList.any {
                it.releaseChannel.hasWellKnown() &&
                        it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
            } -> throw IllegalArgumentException("stable channel metadata must be provided")

            event.app.packageMetadataList.map { it.releaseChannel }
                .distinct()
                .count() != event.app.packageMetadataCount
                -> throw IllegalArgumentException("release channels must not be duplicated")

            !event.app.packageMetadataList.map { it.packageMetadata }
                .all { packageMetadata ->
                    packageMetadata.buildApksResult.variantList.flatMap { it.apkSetList }
                        .flatMap { it.apkDescriptionList }
                        .map { it.path }
                        .all { packageMetadata.objectMetadataMap.contains(it) }
                } -> throw IllegalArgumentException("all objects must have metadata specified")

            !event.app.packageMetadataList.map { it.packageMetadata }
                .all { packageMetadata ->
                    packageMetadata.buildApksResult.variantList.flatMap { it.apkSetList }
                        .flatMap { it.apkDescriptionList }
                        .map { it.path }
                        .all { packageMetadata.objectMetadataMap[it]?.hasUncompressedSize() == true }
                } ->
                throw IllegalArgumentException("all objects must have an uncompressed size specified")

            !event.app.packageMetadataList.map { it.packageMetadata }
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
    }
}
