// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import app.accrescent.events.v1.appListingViewed
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for app [AppListingViewed] events.
 */
class AppListingViewedSerializer : Serializer<AppListingViewed> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppListingViewed): ByteArray {
        return appListingViewed {
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
