// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.events.v1.AppPublished
import org.apache.kafka.common.serialization.Deserializer

class TestAppPublishedDeserializer : Deserializer<AppPublished> {
    override fun deserialize(topic: String, data: ByteArray): AppPublished {
        return AppPublished.parseFrom(data)
    }
}
