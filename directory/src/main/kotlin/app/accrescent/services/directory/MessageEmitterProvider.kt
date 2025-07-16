// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.services.directory.data.events.AppDownloaded
import app.accrescent.services.directory.data.events.AppListingViewed
import app.accrescent.services.directory.data.events.AppUpdateAvailabilityChecked
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
 * @property appDownloadedEmitter emitter for [AppDownloaded] events
 * @property appListingViewedEmitter emitter for [AppListingViewed] events
 * @property appUpdateAvailabilityCheckedEmitter emitter for [AppUpdateAvailabilityChecked] events
 */
@ApplicationScoped
class MessageEmitterProvider(
    @Channel("app-downloaded")
    val appDownloadedEmitter: MutinyEmitter<AppDownloaded>,

    @Channel("app-listing-viewed")
    val appListingViewedEmitter: MutinyEmitter<AppListingViewed>,

    @Channel("app-update-availability-checked")
    val appUpdateAvailabilityCheckedEmitter: MutinyEmitter<AppUpdateAvailabilityChecked>,
)
