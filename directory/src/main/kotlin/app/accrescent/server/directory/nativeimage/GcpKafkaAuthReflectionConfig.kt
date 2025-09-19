// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.nativeimage

import com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler
import io.quarkus.runtime.annotations.RegisterForReflection

/**
 * Reflection configuration to enable GraalVM Native Image support for
 * managed-kafka-auth-login-handler
 */
// This class is used by Quarkus since it's annotated with @RegisterForReflection
@Suppress("unused")
@RegisterForReflection(targets = [GcpLoginCallbackHandler::class])
class GcpKafkaAuthReflectionConfig
