// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import org.apache.kafka.common.serialization.Serializer

class TestAppPublicationRequestedSerializer : Serializer<AppPublicationRequested> {
    override fun serialize(topic: String, data: AppPublicationRequested): ByteArray {
        return data.toByteArray()
    }
}
