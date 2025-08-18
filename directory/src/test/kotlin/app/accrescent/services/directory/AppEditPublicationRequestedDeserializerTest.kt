// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.services.directory.serde.AppEditPublicationRequestedDeserializer
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import com.google.protobuf.InvalidProtocolBufferException
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@QuarkusTest
class AppEditPublicationRequestedDeserializerTest {
    @ParameterizedTest
    @MethodSource("app.accrescent.services.directory.TestDataHelper#generateInvalidAppEditPublicationRequested")
    fun deserializeRejectsInvalidFields(event: AppEditPublicationRequested) {
        assertThrows<IllegalArgumentException> {
            AppEditPublicationRequestedDeserializer().deserialize(
                "",
                event.toByteArray(),
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun deserializeRejectsInvalidBytes() {
        assertThrows<InvalidProtocolBufferException> {
            AppEditPublicationRequestedDeserializer().deserialize(
                "",
                "deadbeefffffffff".hexToByteArray(),
            )
        }
    }

    @Test
    fun deserializeAcceptsValidMessage() {
        assertDoesNotThrow {
            AppEditPublicationRequestedDeserializer().deserialize(
                "",
                TestDataHelper.validAppEditPublicationRequested.toByteArray(),
            )
        }
    }
}
