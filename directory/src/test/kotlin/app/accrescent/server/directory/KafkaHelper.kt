// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.server.directory.serde.AppEditPublishedSerializer
import app.accrescent.server.directory.serde.AppPublishedSerializer
import app.accrescent.server.directory.serde.TestAppEditPublicationRequestedDeserializer
import app.accrescent.server.directory.serde.TestAppEditPublicationRequestedSerializer
import app.accrescent.server.directory.serde.TestAppEditPublishedDeserializer
import app.accrescent.server.directory.serde.TestAppPublicationRequestedDeserializer
import app.accrescent.server.directory.serde.TestAppPublicationRequestedSerializer
import app.accrescent.server.directory.serde.TestAppPublishedDeserializer
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppEditPublished
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppPublished
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

object KafkaHelper {
    const val TOPIC_APP_EDIT_PUBLICATION_REQUESTED = "app-edit-publication-requested"
    const val TOPIC_APP_PUBLICATION_REQUESTED = "app-publication-requested"
    const val TOPIC_APP_EDIT_PUBLISHED = "app-edit-published"
    const val TOPIC_APP_PUBLISHED = "app-published"

    val kafkaConsumerGroupId = UUID.randomUUID().toString()

    fun registerSerdes(companion: KafkaCompanion) {
        companion.registerSerde(
            AppEditPublicationRequested::class.java,
            TestAppEditPublicationRequestedSerializer(),
            TestAppEditPublicationRequestedDeserializer(),
        )
        companion.registerSerde(
            AppPublicationRequested::class.java,
            TestAppPublicationRequestedSerializer(),
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
