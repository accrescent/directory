// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import org.apache.kafka.common.serialization.Deserializer

/**
 * Kafka deserializer for [AppPublicationRequested] events.
 *
 * In addition to basic deserialization, this deserializer also performs validation on all fields of
 * the event. Thus, event consumers can safely assume the event has already been validated when they
 * receive it.
 */
class AppPublicationRequestedDeserializer : Deserializer<AppPublicationRequested> {
    /**
     * @suppress
     */
    override fun deserialize(topic: String, data: ByteArray): AppPublicationRequested {
        val message = AppPublicationRequested.parseFrom(data)

        validateEvent(message)

        return message
    }

    private companion object {
        private fun validateEvent(event: AppPublicationRequested) {
            if (!event.hasApp()) {
                throw IllegalArgumentException("app metadata is missing but required")
            }

            validateApp(event.app)
        }
    }
}
