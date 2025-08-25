// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.server.directory.data.AppRepository
import build.buf.protovalidate.ValidatorFactory
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.vertx.RunOnVertxContext
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier

@QuarkusTest
class AppEditPublicationRequestedProcessorTest {
    @InjectKafkaCompanion
    lateinit var kafka: KafkaCompanion

    @Inject
    private lateinit var appRepository: AppRepository

    private val protoValidator = ValidatorFactory.newBuilder().build()

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

    @Test
    fun publishEditProducesCorrectAppEditPublished() {
        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val appEditPublished = KafkaHelper
            .publishEdits(kafka, TestDataHelper.validAppEditPublicationRequested)
            .firstRecord
            .value()

        assertEquals(TestDataHelper.validAppEditPublicationRequested.edit, appEditPublished.edit)
        assertTrue(protoValidator.validate(appEditPublished).isSuccess)
    }

    @Test
    fun publishEditIsIdempotent() {
        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val appEditPublished1 = KafkaHelper
            .publishEdits(kafka, TestDataHelper.validAppEditPublicationRequested)
            .firstRecord
            .value()
        val appEditPublished2 = KafkaHelper
            .publishEdits(kafka, TestDataHelper.validAppEditPublicationRequested)
            .firstRecord
            .value()

        assertEquals(
            TestDataHelper.validAppEditPublicationRequested.edit,
            appEditPublished1.edit,
        )
        assertTrue(protoValidator.validate(appEditPublished1).isSuccess)
        assertEquals(
            TestDataHelper.validAppEditPublicationRequested.edit,
            appEditPublished2.edit,
        )
        assertTrue(protoValidator.validate(appEditPublished2).isSuccess)
    }
}
