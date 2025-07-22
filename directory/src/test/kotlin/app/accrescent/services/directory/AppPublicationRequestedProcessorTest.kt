// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.events.v1.AppPublicationRequested
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.kafka.KafkaCompanionResource
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.RecordDeserializationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource::class)
class AppPublicationRequestedProcessorTest {
    @InjectKafkaCompanion
    lateinit var kafka: KafkaCompanion

    @BeforeEach
    fun registerKafkaSerdes() {
        KafkaHelper.registerSerdes(kafka)
    }

    @ParameterizedTest
    @MethodSource("app.accrescent.services.directory.TestDataHelper#generateInvalidAppPublicationRequested")
    fun publishAppReportsErrorOnInvalidFields(event: AppPublicationRequested) {
        kafka
            .produce(AppPublicationRequested::class.java)
            .fromRecords(ProducerRecord(KafkaHelper.TOPIC_APP_PUBLICATION_REQUESTED, event))
            .awaitCompletion()

        val badRecord = kafka
            .consume(AppPublicationRequested::class.java)
            .withAutoCommit()
            .withGroupId(KafkaHelper.kafkaConsumerGroupId)
            .fromTopics(KafkaHelper.TOPIC_DEAD_LETTER_APP_PUBLICATION_REQUESTED, 1)
            .awaitCompletion()
            .firstRecord

        val errorType = badRecord
            .headers()
            .lastHeader(KafkaHelper.HEADER_DEAD_LETTER_EXCEPTION_CLASS)
            ?.value()
            ?.toString(Charsets.UTF_8)

        assertEquals(event.app, badRecord.value().app)
        assertEquals(RecordDeserializationException::class.qualifiedName, errorType)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun publishAppReportsErrorOnInvalidBytes() {
        val invalidByteSequence = "ffffffffdeadbeef".hexToByteArray()

        kafka
            .produce(ByteArray::class.java)
            .fromRecords(
                ProducerRecord(
                    KafkaHelper.TOPIC_APP_PUBLICATION_REQUESTED,
                    invalidByteSequence,
                ),
            )
            .awaitCompletion()

        val badRecord = kafka
            .consume(ByteArray::class.java)
            .withAutoCommit()
            .withGroupId(KafkaHelper.kafkaConsumerGroupId)
            .fromTopics(KafkaHelper.TOPIC_DEAD_LETTER_APP_PUBLICATION_REQUESTED, 1)
            .awaitCompletion()
            .firstRecord

        val errorType = badRecord
            .headers()
            .lastHeader(KafkaHelper.HEADER_DEAD_LETTER_EXCEPTION_CLASS)
            ?.value()
            ?.toString(Charsets.UTF_8)

        assertArrayEquals(invalidByteSequence, badRecord.value())
        assertEquals(RecordDeserializationException::class.qualifiedName, errorType)
    }

    @Test
    fun publishAppProducesCorrectAppPublished() {
        val appPublished = KafkaHelper
            .publishApps(kafka, TestDataHelper.validAppPublicationRequested)
            .firstRecord
            .value()

        assertEquals(TestDataHelper.validAppPublicationRequested.app, appPublished.app)
    }

    @Test
    fun publishAppIsIdempotent() {
        val appPublishedEvents = KafkaHelper
            .publishApps(
                kafka,
                TestDataHelper.validAppPublicationRequested,
                TestDataHelper.validAppPublicationRequested,
            )
            .records
            .map { it.value() }

        assertEquals(TestDataHelper.validAppPublicationRequested.app, appPublishedEvents[0].app)
        assertEquals(TestDataHelper.validAppPublicationRequested.app, appPublishedEvents[1].app)
    }
}
