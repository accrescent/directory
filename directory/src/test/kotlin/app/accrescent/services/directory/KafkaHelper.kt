// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.events.v1.AppEditPublicationRequested
import app.accrescent.events.v1.AppEditPublished
import app.accrescent.events.v1.AppPublicationRequested
import app.accrescent.events.v1.AppPublished
import app.accrescent.services.directory.serde.AppEditPublicationRequestedSerializer
import app.accrescent.services.directory.serde.AppEditPublishedSerializer
import app.accrescent.services.directory.serde.AppPublicationRequestedSerializer
import app.accrescent.services.directory.serde.AppPublishedSerializer
import app.accrescent.services.directory.serde.TestAppEditPublicationRequestedDeserializer
import app.accrescent.services.directory.serde.TestAppEditPublishedDeserializer
import app.accrescent.services.directory.serde.TestAppPublicationRequestedDeserializer
import app.accrescent.services.directory.serde.TestAppPublishedDeserializer
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

object KafkaHelper {
    const val HEADER_DEAD_LETTER_EXCEPTION_CLASS = "dead-letter-exception-class-name"
    const val TOPIC_APP_EDIT_PUBLICATION_REQUESTED = "app-edit-publication-requested"
    const val TOPIC_APP_PUBLICATION_REQUESTED = "app-publication-requested"
    const val TOPIC_APP_EDIT_PUBLISHED = "app-edit-published"
    const val TOPIC_APP_PUBLISHED = "app-published"
    const val TOPIC_DEAD_LETTER_APP_EDIT_PUBLICATION_REQUESTED = "dead-letter-topic-app-edit-publication-requested"
    const val TOPIC_DEAD_LETTER_APP_PUBLICATION_REQUESTED = "dead-letter-topic-app-publication-requested"

    val kafkaConsumerGroupId = UUID.randomUUID().toString()

    fun registerSerdes(companion: KafkaCompanion) {
        companion.registerSerde(
            AppEditPublicationRequested::class.java,
            AppEditPublicationRequestedSerializer(),
            TestAppEditPublicationRequestedDeserializer(),
        )
        companion.registerSerde(
            AppPublicationRequested::class.java,
            AppPublicationRequestedSerializer(),
            TestAppPublicationRequestedDeserializer(),
        )
        companion.registerSerde(
            AppEditPublished::class.java,
            AppEditPublishedSerializer(),
            TestAppEditPublishedDeserializer(),
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
            .fromRecords(events.map { ProducerRecord(TOPIC_APP_PUBLICATION_REQUESTED, it) })
            .awaitCompletion()

        return companion
            .consume(AppPublished::class.java)
            .withAutoCommit()
            .withGroupId(kafkaConsumerGroupId)
            .fromTopics(TOPIC_APP_PUBLISHED, events.size.toLong())
            .awaitCompletion()
    }

    fun publishEdits(
        companion: KafkaCompanion,
        vararg events: AppEditPublicationRequested,
    ): ConsumerTask<String, AppEditPublished> {
        companion.produce(AppEditPublicationRequested::class.java)
            .fromRecords(events.map { ProducerRecord(TOPIC_APP_EDIT_PUBLICATION_REQUESTED, it) })
            .awaitCompletion()

        return companion
            .consume(AppEditPublished::class.java)
            .withAutoCommit()
            .withGroupId(kafkaConsumerGroupId)
            .fromTopics(TOPIC_APP_EDIT_PUBLISHED, events.size.toLong())
            .awaitCompletion()
    }
}
