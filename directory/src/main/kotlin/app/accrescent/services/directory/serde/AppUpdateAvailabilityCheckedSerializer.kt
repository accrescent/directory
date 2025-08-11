// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.server.events.v1.appUpdateAvailabilityChecked
import app.accrescent.services.directory.data.events.AppUpdateAvailabilityChecked
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for app [AppUpdateAvailabilityChecked] events.
 */
class AppUpdateAvailabilityCheckedSerializer : Serializer<AppUpdateAvailabilityChecked> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppUpdateAvailabilityChecked): ByteArray {
        return appUpdateAvailabilityChecked {
            date = data.date.toString()
            appId = data.appId
            releaseChannel = data.releaseChannel
            deviceSdkVersion = data.deviceSdkVersion.toInt()
            if (data.countryCode != null) {
                countryCode = data.countryCode
            }
        }.toByteArray()
    }
}
