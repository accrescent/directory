// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.server.directory.serde.AppPublicationRequestedDeserializer
import com.google.protobuf.InvalidProtocolBufferException
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@QuarkusTest
class AppPublicationRequestedDeserializerTest {
    @Test
    fun deserializeRejectsInvalidFields() {
        assertThrows<IllegalArgumentException> {
            AppPublicationRequestedDeserializer().deserialize(
                "",
                TestDataHelper.invalidAppPublicationRequested.toByteArray(),
            )
        }
    }

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
