// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppPublished
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for [AppPublished] events.
 */
class AppPublishedSerializer : Serializer<AppPublished> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppPublished): ByteArray {
        return data.toByteArray()
    }
}
