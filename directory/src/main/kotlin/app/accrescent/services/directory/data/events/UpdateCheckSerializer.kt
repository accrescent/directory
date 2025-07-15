// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import app.accrescent.events.v1.AppUpdateCheckEvent
import app.accrescent.events.v1.appUpdateCheckEvent
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for app [UpdateCheck] events.
 *
 * Serializes to protobuf instances of [AppUpdateCheckEvent].
 */
class UpdateCheckSerializer : Serializer<UpdateCheck> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: UpdateCheck): ByteArray {
        return appUpdateCheckEvent {
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
