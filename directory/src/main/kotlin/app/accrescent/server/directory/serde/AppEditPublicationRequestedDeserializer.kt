// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.serde

import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.protovalidate.ValidatorFactory
import org.apache.kafka.common.serialization.Deserializer

/**
 * Kafka deserializer for [AppEditPublicationRequested] events.
 *
 * In addition to basic deserialization, this deserializer also performs validation on all fields of
 * the event. Thus, event consumers can safely assume the event has already been validated when they
 * receive it.
 */
class AppEditPublicationRequestedDeserializer : Deserializer<AppEditPublicationRequested> {
    private val validator = ValidatorFactory
        .newBuilder()
        .buildWithDescriptors(listOf(AppEditPublicationRequested.getDescriptor()), true)

    /**
     * @suppress
     */
    override fun deserialize(topic: String, data: ByteArray): AppEditPublicationRequested {
        val message = AppEditPublicationRequested.parseFrom(data)

        val validationResult = validator.validate(message)
        if (!validationResult.isSuccess) {
            throw IllegalArgumentException(validationResult.toString())
        }

        return message
    }
}
