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
import build.buf.gen.accrescent.server.events.v1.AppKt.packageMetadataEntry
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppPublished
import build.buf.gen.accrescent.server.events.v1.app
import build.buf.gen.accrescent.server.events.v1.appListing
import build.buf.gen.accrescent.server.events.v1.appPublished
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
 * Processor for [AppPublicationRequested] events which publishes apps to the directory.
 *
 * This class consumes [AppPublicationRequested] events and publishes the app data therein to the
 * directory as a new app. Upon successfully processing the event, it subsequently publishes an
 * [AppPublished] event with the same app data.
 */
@ApplicationScoped
class AppPublicationRequestedProcessor(
    private val appRepository: AppRepository,

    @Channel("app-published")
    // bufferSize must be greater than or equal to app-publication-requested.max.poll.records to
    // ensure our buffer doesn't overflow
    @OnOverflow(OnOverflow.Strategy.BUFFER, bufferSize = 500)
    private val appPublishedProducer: KafkaTransactions<AppPublished>,
) {
    /**
     * Publishes [AppPublicationRequested] events to the directory as new apps.
     *
     * This method has exactly-once Kafka message delivery semantics. It always attempts to commit
     * its database transaction before committing its consumer offset or its [AppPublished] output.
     */
    @Incoming("app-publication-requested")
    fun publishApp(batch: Message<List<AppPublicationRequested>>): Uni<Void> {
        return appPublishedProducer.withTransactionAndAck(batch) { emitter ->
            val apps = batch.payload.map { event ->
                App(
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
                    releaseChannels = event.app.packageMetadataList.mapTo(mutableSetOf()) {
                        val releaseChannelId = UUID.randomUUID()

                        ReleaseChannel(
                            id = releaseChannelId,
                            appId = event.app.appId,
                            name = it.releaseChannel.canonicalForm(),
                            versionCode = it.packageMetadata.versionCode.toULong(),
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
            }

            Panache.withTransaction {
                appRepository
                    .deleteByIds(apps.map { it.id })
                    .chain { -> appRepository.persist(apps) }
            }.invoke { ->
                apps.map { app ->
                    appPublished {
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
                                    releaseChannel = channel.name.toReleaseChannel()
                                    packageMetadata = packageMetadata {
                                        versionCode = channel.versionCode.toLong()
                                        versionName = channel.versionName
                                        buildApksResult = BuildApksResult.parseFrom(channel.buildApksResult)
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
                }.forEach {
                    emitter.send(it)
                }
            }
        }
    }
}
