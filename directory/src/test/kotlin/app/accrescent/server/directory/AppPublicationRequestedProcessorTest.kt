// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.server.directory.data.AppRepository
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.kafka.KafkaCompanionResource
import io.quarkus.test.vertx.RunOnVertxContext
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource::class)
class AppPublicationRequestedProcessorTest {
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
        val appPublished1 = KafkaHelper
            .publishApps(kafka, TestDataHelper.validAppPublicationRequested)
            .firstRecord
            .value()
        val appPublished2 = KafkaHelper
            .publishApps(kafka, TestDataHelper.validAppPublicationRequested)
            .firstRecord
            .value()

        assertEquals(TestDataHelper.validAppPublicationRequested.app, appPublished1.app)
        assertEquals(TestDataHelper.validAppPublicationRequested.app, appPublished2.app)
    }
}
