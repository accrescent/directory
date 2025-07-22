// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.events.v1.AppPublicationRequested
import app.accrescent.events.v1.AppPublished
import app.accrescent.services.directory.serde.AppPublicationRequestedSerializer
import app.accrescent.services.directory.serde.AppPublishedSerializer
import app.accrescent.services.directory.serde.TestAppPublicationRequestedDeserializer
import app.accrescent.services.directory.serde.TestAppPublishedDeserializer
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

object KafkaHelper {
    val kafkaConsumerGroupId = UUID.randomUUID().toString()

    fun registerSerdes(companion: KafkaCompanion) {
        companion.registerSerde(
            AppPublicationRequested::class.java,
            AppPublicationRequestedSerializer(),
            TestAppPublicationRequestedDeserializer(),
        )
        companion.registerSerde(
            AppPublished::class.java,
            AppPublishedSerializer(),
            TestAppPublishedDeserializer(),
        )
    }

    fun publishApps(
        companion: KafkaCompanion,
        vararg events: AppPublicationRequested,
    ): ConsumerTask<String, AppPublished> {
        companion.produce(AppPublicationRequested::class.java)
            .fromRecords(events.map { ProducerRecord("app-publication-requested", it) })
            .awaitCompletion()

        return companion
            .consume(AppPublished::class.java)
            .withAutoCommit()
            .withGroupId(kafkaConsumerGroupId)
            .fromTopics("app-published", events.size.toLong())
            .awaitCompletion()
    }
}
