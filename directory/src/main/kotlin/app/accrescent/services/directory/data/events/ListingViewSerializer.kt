// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import app.accrescent.events.v1.AppListingViewEvent
import app.accrescent.events.v1.appListingViewEvent
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for app [ListingView] events.
 *
 * Serializes to protobuf instances of [AppListingViewEvent].
 */
class ListingViewSerializer : Serializer<ListingView> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: ListingView): ByteArray {
        return appListingViewEvent {
            date = data.date.toString()
            appId = data.appId
            languageCode = data.languageCode
            deviceSdkVersion = data.deviceSdkVersion.toInt()
            if (data.countryCode != null) {
                countryCode = data.countryCode
            }
        }.toByteArray()
    }
}
