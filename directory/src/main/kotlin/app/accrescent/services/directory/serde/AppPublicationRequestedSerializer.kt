// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.events.v1.AppPublicationRequested
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for [AppPublicationRequested] events.
 */
class AppPublicationRequestedSerializer : Serializer<AppPublicationRequested> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppPublicationRequested): ByteArray {
        return data.toByteArray()
    }
}
