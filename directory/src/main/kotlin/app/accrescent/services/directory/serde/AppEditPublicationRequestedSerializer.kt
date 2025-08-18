// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for [AppEditPublicationRequested] events.
 */
class AppEditPublicationRequestedSerializer : Serializer<AppEditPublicationRequested> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppEditPublicationRequested): ByteArray {
        return data.toByteArray()
    }
}
