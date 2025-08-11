// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.server.events.v1.AppEditPublicationRequested
import app.accrescent.services.directory.data.AppRepository
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.vertx.RunOnVertxContext
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import jakarta.inject.Inject
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.RecordDeserializationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.function.Supplier

@QuarkusTest
class AppEditPublicationRequestedProcessorTest {
    @InjectKafkaCompanion
    lateinit var kafka: KafkaCompanion

    @Inject
    private lateinit var appRepository: AppRepository

    @BeforeEach
    @RunOnVertxContext
    fun cleanDatabase(asserter: TransactionalUniAsserter) {
        asserter.execute(Supplier { cleanDatabase() })
    }

    @WithTransaction
    fun cleanDatabase(): Uni<Long> {
        return appRepository.deleteAll()
    }

    @BeforeEach
    fun registerKafkaSerdes() {
        KafkaHelper.registerSerdes(kafka)
    }

    @ParameterizedTest
    @MethodSource("app.accrescent.services.directory.TestDataHelper#generateInvalidAppEditPublicationRequested")
    fun publishEditReportsErrorOnInvalidFields(event: AppEditPublicationRequested) {
        kafka
            .produce(AppEditPublicationRequested::class.java)
            .fromRecords(ProducerRecord(KafkaHelper.TOPIC_APP_EDIT_PUBLICATION_REQUESTED, event))
            .awaitCompletion()

        val badRecord = kafka
            .consume(AppEditPublicationRequested::class.java)
            .withAutoCommit()
            .withGroupId(KafkaHelper.kafkaConsumerGroupId)
            .fromTopics(KafkaHelper.TOPIC_DEAD_LETTER_APP_EDIT_PUBLICATION_REQUESTED, 1)
            .awaitCompletion()
            .firstRecord

        val errorType = badRecord
            .headers()
            .lastHeader(KafkaHelper.HEADER_DEAD_LETTER_EXCEPTION_CLASS)
            ?.value()
            ?.toString(Charsets.UTF_8)

        assertEquals(event.edit, badRecord.value().edit)
        assertEquals(RecordDeserializationException::class.qualifiedName, errorType)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun publishEditReportsErrorOnInvalidBytes() {
        val invalidByteSequence = "deadbeefffffffff".hexToByteArray()

        kafka
            .produce(ByteArray::class.java)
            .fromRecords(
                ProducerRecord(
                    KafkaHelper.TOPIC_APP_EDIT_PUBLICATION_REQUESTED,
                    invalidByteSequence,
                ),
            )
            .awaitCompletion()

        val badRecord = kafka
            .consume(ByteArray::class.java)
            .withAutoCommit()
            .withGroupId(KafkaHelper.kafkaConsumerGroupId)
            .fromTopics(KafkaHelper.TOPIC_DEAD_LETTER_APP_EDIT_PUBLICATION_REQUESTED, 1)
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
    fun publishEditProducesCorrectAppEditPublished() {
        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val appEditPublished = KafkaHelper
            .publishEdits(kafka, TestDataHelper.validAppEditPublicationRequested)
            .firstRecord
            .value()

        assertEquals(TestDataHelper.validAppEditPublicationRequested.edit, appEditPublished.edit)
    }

    @Test
    fun publishEditIsIdempotent() {
        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val appEditPublishedEvents = KafkaHelper
            .publishEdits(
                kafka,
                TestDataHelper.validAppEditPublicationRequested,
                TestDataHelper.validAppEditPublicationRequested,
            )
            .records
            .map { it.value() }

        assertEquals(
            TestDataHelper.validAppEditPublicationRequested.edit,
            appEditPublishedEvents[0].edit,
        )
        assertEquals(
            TestDataHelper.validAppEditPublicationRequested.edit,
            appEditPublishedEvents[1].edit,
        )
    }
}
