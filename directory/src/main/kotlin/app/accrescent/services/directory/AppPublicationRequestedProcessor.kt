// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.com.android.bundle.Commands
import app.accrescent.events.v1.AppKt.packageMetadataEntry
import app.accrescent.events.v1.AppPublicationRequested
import app.accrescent.events.v1.AppPublished
import app.accrescent.events.v1.app
import app.accrescent.events.v1.appListing
import app.accrescent.events.v1.appPublished
import app.accrescent.events.v1.image
import app.accrescent.events.v1.objectMetadata
import app.accrescent.events.v1.packageMetadata
import app.accrescent.services.directory.data.App
import app.accrescent.services.directory.data.AppRepository
import app.accrescent.services.directory.data.Image
import app.accrescent.services.directory.data.Listing
import app.accrescent.services.directory.data.ListingId
import app.accrescent.services.directory.data.ReleaseChannel
import app.accrescent.services.directory.data.StorageObject
import io.quarkus.hibernate.reactive.panache.Panache
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Outgoing
import java.util.UUID

/**
 * Processor for [AppPublicationRequested] events which publishes apps to the directory.
 *
 * This class consumes [AppPublicationRequested] events and publishes the app data therein to the
 * directory as a new app. Upon successfully processing the event, it subsequently publishes an
 * [AppPublished] event with the same app data.
 */
@ApplicationScoped
class AppPublicationRequestedProcessor(
    private val appRepository: AppRepository,
) {
    /**
     * Publishes [AppPublicationRequested] events to the directory as new apps.
     *
     * This method has at-least-once Kafka message delivery semantics. It always attempts to commit
     * its database transaction before committing its consumer offset or its [AppPublished] output.
     */
    @Incoming("app-publication-requested")
    @Outgoing("app-published")
    fun publishApp(event: AppPublicationRequested): Uni<AppPublished> {
        val app = App(
            id = event.app.appId,
            defaultListingLanguage = event.app.defaultListingLanguage,
            listings = event.app.listingsList.mapTo(mutableSetOf()) {
                Listing(
                    id = ListingId(
                        appId = event.app.appId,
                        language = it.language,
                    ),
                    name = it.name,
                    shortDescription = it.shortDescription,
                    icon = Image(
                        objectId = it.icon.objectId,
                    ),
                )
            },
            releaseChannels = event.app.packageMetadataList.mapTo(mutableSetOf()) { it ->
                val releaseChannelId = UUID.randomUUID()

                ReleaseChannel(
                    id = releaseChannelId,
                    appId = event.app.appId,
                    name = it.releaseChannel.canonicalForm(),
                    versionCode = it.packageMetadata.versionCode.toUInt(),
                    versionName = it.packageMetadata.versionName,
                    buildApksResult = it.packageMetadata.buildApksResult.toByteArray(),
                    objects = it.packageMetadata.objectMetadataMap
                        .mapTo(mutableSetOf()) {
                            StorageObject(
                                id = it.key,
                                releaseChannelId = releaseChannelId,
                                uncompressedSize = it.value.uncompressedSize.toUInt(),
                            )
                        },
                )
            },
        )

        val result = Panache.withTransaction {
            appRepository.deleteById(app.id).chain { -> appRepository.persist(app) }
        }.map { dbApp ->
            appPublished {
                this.app = app {
                    appId = dbApp.id
                    defaultListingLanguage = dbApp.defaultListingLanguage
                    listings.addAll(dbApp.listings.map {
                        appListing {
                            language = it.id.language
                            name = it.name
                            shortDescription = it.shortDescription
                            icon = image { objectId = it.icon.objectId }
                        }
                    })
                    packageMetadata.addAll(dbApp.releaseChannels.map { channel ->
                        packageMetadataEntry {
                            releaseChannel = channel.name.toEventsReleaseChannel()
                            packageMetadata = packageMetadata {
                                versionCode = channel.versionCode.toInt()
                                versionName = channel.versionName
                                buildApksResult =
                                    Commands.BuildApksResult.parseFrom(channel.buildApksResult)
                                objectMetadata.putAll(channel.objects.associate {
                                    it.id to objectMetadata {
                                        uncompressedSize = it.uncompressedSize.toInt()
                                    }
                                })
                            }
                        }
                    })
                }
            }
        }

        return result
    }
}
