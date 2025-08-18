// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import org.apache.kafka.common.serialization.Deserializer

/**
 * Kafka deserializer for [AppEditPublicationRequested] events.
 *
 * In addition to basic deserialization, this deserializer also performs validation on all fields of
 * the event. Thus, event consumers can safely assume the event has already been validated when they
 * receive it.
 */
class AppEditPublicationRequestedDeserializer : Deserializer<AppEditPublicationRequested> {
    /**
     * @suppress
     */
    override fun deserialize(topic: String, data: ByteArray): AppEditPublicationRequested {
        val message = AppEditPublicationRequested.parseFrom(data)

        validateEvent(message)

        return message
    }

    private companion object {
        private fun validateEvent(event: AppEditPublicationRequested) {
            when {
                !event.hasEdit() -> throw IllegalArgumentException("edit metadata is missing but required")
                !event.edit.hasId() -> throw IllegalArgumentException("edit ID is missing but required")
                !event.edit.hasApp() -> throw IllegalArgumentException("edit app is missing but required")
            }

            validateApp(event.edit.app)
        }
    }
}
