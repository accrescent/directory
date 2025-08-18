// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import org.apache.kafka.common.serialization.Deserializer

class TestAppPublicationRequestedDeserializer : Deserializer<AppPublicationRequested> {
    override fun deserialize(topic: String, data: ByteArray): AppPublicationRequested {
        return AppPublicationRequested.parseFrom(data)
    }
}
