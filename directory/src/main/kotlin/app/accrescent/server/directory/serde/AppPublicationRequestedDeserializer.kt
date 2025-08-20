// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import build.buf.protovalidate.ValidatorFactory
import org.apache.kafka.common.serialization.Deserializer

/**
 * Kafka deserializer for [AppPublicationRequested] events.
 *
 * In addition to basic deserialization, this deserializer also performs validation on all fields of
 * the event. Thus, event consumers can safely assume the event has already been validated when they
 * receive it.
 */
class AppPublicationRequestedDeserializer : Deserializer<AppPublicationRequested> {
    private val validator = ValidatorFactory
        .newBuilder()
        .buildWithDescriptors(listOf(AppPublicationRequested.getDescriptor()), true)

    /**
     * @suppress
     */
    override fun deserialize(topic: String, data: ByteArray): AppPublicationRequested {
        val message = AppPublicationRequested.parseFrom(data)

        val validationResult = validator.validate(message)
        if (!validationResult.isSuccess) {
            throw IllegalArgumentException("message did not pass validation")
        }

        return message
    }
}
