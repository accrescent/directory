// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.server.directory.data.Apk
import app.accrescent.server.directory.data.App
import app.accrescent.server.directory.data.AppRepository
import app.accrescent.server.directory.data.Image
import app.accrescent.server.directory.data.Listing
import app.accrescent.server.directory.data.ListingId
import app.accrescent.server.directory.data.ReleaseChannel
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
import io.smallrye.reactive.messaging.kafka.transactions.KafkaTransactions
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.eclipse.microprofile.reactive.messaging.Message
import org.eclipse.microprofile.reactive.messaging.OnOverflow
import java.util.UUID

/**
 * Processor for [AppEditPublicationRequested] events which publishes app edits to the directory.
 *
 * This class consumes [AppEditPublicationRequested] events and publishes the edit data therein to
 * the directory onto the appropriate app. Upon successfully processing the event, it subsequently
 * publishes an [AppEditPublished] event with the same app data.
 */
@ApplicationScoped
class AppEditPublicationRequestedProcessor(
    private val appRepository: AppRepository,

    @Channel("app-edit-published")
    // bufferSize must be greater than or equal to app-edit-publication-requested.max.poll.records
    // to ensure our buffer doesn't overflow
    @OnOverflow(OnOverflow.Strategy.BUFFER, bufferSize = 500)
    private val appEditPublishedProducer: KafkaTransactions<AppEditPublished>,
) {
    /**
     * Publishes [AppEditPublicationRequested] events to the directory onto existing apps.
     *
     * This method has exactly-once Kafka message delivery semantics. It always attempts to commit
     * its database transaction before committing its consumer offset or its [AppEditPublished]
     * output.
     */
    @Incoming("app-edit-publication-requested")
    fun publishEdit(batch: Message<List<AppEditPublicationRequested>>): Uni<Void> {
        return appEditPublishedProducer.withTransactionAndAck(batch) { emitter ->
            val editToAppMappings = batch.payload.map { event ->
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
                            apks = it.packageMetadata.apkObjectMetadataMap
                                .mapTo(mutableSetOf()) { (apkSetPath, objectMetadata) ->
                                    Apk(
                                        apkSetPath = apkSetPath,
                                        objectId = objectMetadata.id,
                                        releaseChannelId = releaseChannelId,
                                        uncompressedSize = objectMetadata.uncompressedSize.toUInt(),
                                    )
                                },
                        )
                    },
                )

                event.edit.id to app
            }

            Panache.withTransaction {
                appRepository
                    .deleteByIds(editToAppMappings.map { it.second.id })
                    .chain { -> appRepository.persist(editToAppMappings.map { it.second }) }
            }.invoke { ->
                editToAppMappings.map { (editId, app) ->
                    appEditPublished {
                        this.edit = appEdit {
                            this.id = editId
                            this.app = app {
                                appId = app.id
                                defaultListingLanguage = app.defaultListingLanguage
                                listings.addAll(app.listings.map {
                                    appListing {
                                        language = it.id.language
                                        name = it.name
                                        shortDescription = it.shortDescription
                                        icon = image { objectId = it.icon.objectId }
                                    }
                                })
                                packageMetadata.addAll(app.releaseChannels.map { channel ->
                                    packageMetadataEntry {
                                        releaseChannel = channel.name.toEventsReleaseChannel()
                                        packageMetadata = packageMetadata {
                                            versionCode = channel.versionCode.toInt()
                                            versionName = channel.versionName
                                            buildApksResult =
                                                BuildApksResult.parseFrom(channel.buildApksResult)
                                            apkObjectMetadata.putAll(channel.apks.associate {
                                                it.apkSetPath to objectMetadata {
                                                    id = it.objectId
                                                    uncompressedSize = it.uncompressedSize.toInt()
                                                }
                                            })
                                        }
                                    }
                                })
                            }
                        }
                    }
                }.forEach {
                    emitter.send(it)
                }
            }
        }
    }
}
