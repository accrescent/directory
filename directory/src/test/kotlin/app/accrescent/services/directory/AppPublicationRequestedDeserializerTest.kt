// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.services.directory.serde.AppPublicationRequestedDeserializer
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import com.google.protobuf.InvalidProtocolBufferException
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@QuarkusTest
class AppPublicationRequestedDeserializerTest {
    @ParameterizedTest
    @MethodSource("app.accrescent.services.directory.TestDataHelper#generateInvalidAppPublicationRequested")
    fun deserializeRejectsInvalidFields(event: AppPublicationRequested) {
        assertThrows<IllegalArgumentException> {
            AppPublicationRequestedDeserializer().deserialize(
                "",
                event.toByteArray(),
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun deserializeRejectsInvalidBytes() {
        assertThrows<InvalidProtocolBufferException> {
            AppPublicationRequestedDeserializer().deserialize(
                "",
                "ffffffffdeadbeef".hexToByteArray(),
            )
        }
    }

    @Test
    fun deserializeAcceptsValidMessage() {
        assertDoesNotThrow {
            AppPublicationRequestedDeserializer().deserialize(
                "",
                TestDataHelper.validAppPublicationRequested.toByteArray(),
            )
        }
    }
}
