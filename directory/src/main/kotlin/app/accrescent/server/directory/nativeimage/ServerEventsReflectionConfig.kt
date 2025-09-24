// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.nativeimage

import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import io.quarkus.runtime.annotations.RegisterForReflection

/**
 * Reflection configuration to enable GraalVM Native Image support for server-events
 */
// This class is used by Quarkus since it's annotated with @RegisterForReflection
@Suppress("unused")
@RegisterForReflection(
    targets = [AppEditPublicationRequested::class, AppPublicationRequested::class],
    registerFullHierarchy = true,
)
class ServerEventsReflectionConfig
