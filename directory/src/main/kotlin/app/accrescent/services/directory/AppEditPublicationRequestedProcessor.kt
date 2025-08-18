// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.services.directory.data.App
import app.accrescent.services.directory.data.AppRepository
import app.accrescent.services.directory.data.Image
import app.accrescent.services.directory.data.Listing
import app.accrescent.services.directory.data.ListingId
import app.accrescent.services.directory.data.ReleaseChannel
import app.accrescent.services.directory.data.StorageObject
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppEditPublished
import build.buf.gen.accrescent.server.events.v1.AppKt.packageMetadataEntry
import build.buf.gen.accrescent.server.events.v1.app
import build.buf.gen.accrescent.server.events.v1.appEdit
import build.buf.gen.accrescent.server.events.v1.appEditPublished
import build.buf.gen.accrescent.server.events.v1.appListing
import build.buf.gen.accrescent.server.events.v1.image
import build.buf.gen.accrescent.server.events.v1.objectMetadata
import build.buf.gen.accrescent.server.events.v1.packageMetadata
import build.buf.gen.android.bundle.BuildApksResult
import io.quarkus.hibernate.reactive.panache.Panache
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Outgoing
import java.util.UUID

/**
 * Processor for [AppEditPublicationRequested] events which publishes app edits to the directory.
 *
 * This class consumes [AppEditPublicationRequested] events and publishes the edit data therein to
 * the directory onto the appropriate app. Upon successfully processing the event, it subsequently
 * publishes an [AppEditPublished] event with the same app data.
 */
@ApplicationScoped
class AppEditPublicationRequestedProcessor(private val appRepository: AppRepository) {
    /**
     * Publishes [AppEditPublicationRequested] events to the directory onto existing apps.
     *
     * This method has at-least-once Kafka message delivery semantics. It always attempts to commit
     * its database transaction before committing its consumer offset or its [AppEditPublished]
     * output.
     */
    @Incoming("app-edit-publication-requested")
    @Outgoing("app-edit-published")
    fun publishEdit(event: AppEditPublicationRequested): Uni<AppEditPublished> {
        val app = App(
            id = event.edit.app.appId,
            defaultListingLanguage = event.edit.app.defaultListingLanguage,
            listings = event.edit.app.listingsList.mapTo(mutableSetOf()) {
                Listing(
                    id = ListingId(
                        appId = event.edit.app.appId,
                        language = it.language,
                    ),
                    name = it.name,
                    shortDescription = it.shortDescription,
                    icon = Image(
                        objectId = it.icon.objectId,
                    ),
                )
            },
            releaseChannels = event.edit.app.packageMetadataList.mapTo(mutableSetOf()) { it ->
                val releaseChannelId = UUID.randomUUID()

                ReleaseChannel(
                    id = releaseChannelId,
                    appId = event.edit.app.appId,
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
            appEditPublished {
                this.edit = appEdit {
                    this.id = event.edit.id
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
                                        BuildApksResult.parseFrom(channel.buildApksResult)
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
        }

        return result
    }
}
