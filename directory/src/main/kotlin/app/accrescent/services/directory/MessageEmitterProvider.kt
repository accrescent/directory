// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.services.directory.data.events.Download
import app.accrescent.services.directory.data.events.ListingView
import app.accrescent.services.directory.data.events.UpdateCheck
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel

/**
 * Wrapper class for Quarkus Messaging emitters.
 *
 * Because of [quarkusio/quarkus#17841](https://github.com/quarkusio/quarkus/issues/17841) and
 * [quarkusio/quarkus#23471](https://github.com/quarkusio/quarkus/issues/23471), we cannot inject
 * [MutinyEmitter]s directly into our [DirectoryServiceImpl] class. As a workaround, we instead
 * inject all emitters we need into this wrapper class and then inject this wrapper class into
 * [DirectoryServiceImpl] and any other classes which need emitters.
 *
 * @property appDownloadEventEmitter event emitter for [Download] events
 * @property appListingViewEventEmitter event emitter for [ListingView] events
 * @property appUpdateCheckEventEmitter event emitter for [UpdateCheck] events
 */
@ApplicationScoped
class MessageEmitterProvider(
    @Channel("app-download-events")
    val appDownloadEventEmitter: MutinyEmitter<Download>,

    @Channel("app-listing-view-events")
    val appListingViewEventEmitter: MutinyEmitter<ListingView>,

    @Channel("app-update-check-events")
    val appUpdateCheckEventEmitter: MutinyEmitter<UpdateCheck>,
)
