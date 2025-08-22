// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.kafka.KafkaCompanionResource
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource::class)
class AppPublicationRequestedProcessorTest {
    @InjectKafkaCompanion
    lateinit var kafka: KafkaCompanion

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
