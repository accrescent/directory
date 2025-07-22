// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.events.v1.AppEditPublished
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for [AppEditPublished] events.
 */
class AppEditPublishedSerializer : Serializer<AppEditPublished> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppEditPublished): ByteArray {
        return data.toByteArray()
    }
}
