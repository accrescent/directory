// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import jakarta.enterprise.context.ApplicationScoped

/**
 * Context key for accessing a requesting client's geographical region
 */
val GEO_REGION_CONTEXT_KEY: Context.Key<String> = Context.key("client-geo-region")

/**
 * Metadata key for accessing a requesting client's geographical region
 */
val GEO_REGION_METADATA_KEY: Metadata.Key<String> =
    Metadata.Key.of("X-Client-Geo-Region", Metadata.ASCII_STRING_MARSHALLER)

/**
 * Interceptor which attaches the client's geographical region to the request context
 *
 * Attachment is based on the "X-Client-Geo-Region" header.
 */
@ApplicationScoped
class RegionMetadataAttacherInterceptor : ServerInterceptor {
    /**
     * @suppress
     */
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val regionHeader = headers.get(GEO_REGION_METADATA_KEY)
        val context = Context.current().withValue(GEO_REGION_CONTEXT_KEY, regionHeader)

        return Contexts.interceptCall(context, call, headers, next)
    }
}
