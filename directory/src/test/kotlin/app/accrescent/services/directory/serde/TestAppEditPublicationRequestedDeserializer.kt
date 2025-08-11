// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.server.events.v1.AppEditPublicationRequested
import org.apache.kafka.common.serialization.Deserializer

class TestAppEditPublicationRequestedDeserializer : Deserializer<AppEditPublicationRequested> {
    override fun deserialize(topic: String, data: ByteArray): AppEditPublicationRequested {
        return AppEditPublicationRequested.parseFrom(data)
    }
}
