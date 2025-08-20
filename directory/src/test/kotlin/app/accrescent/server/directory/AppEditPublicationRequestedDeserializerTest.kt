// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.server.directory.serde.AppEditPublicationRequestedDeserializer
import com.google.protobuf.InvalidProtocolBufferException
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@QuarkusTest
class AppEditPublicationRequestedDeserializerTest {
    @Test
    fun deserializeRejectsInvalidFields() {
        assertThrows<IllegalArgumentException> {
            AppEditPublicationRequestedDeserializer().deserialize(
                "",
                TestDataHelper.invalidAppEditPublicationRequested.toByteArray(),
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
