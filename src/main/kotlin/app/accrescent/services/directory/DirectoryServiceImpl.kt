// Copyright 2024-2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.internal.v1.CreateAppRequest
import app.accrescent.directory.internal.v1.CreateAppResponse
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.GetAppDownloadInfoRequest
import app.accrescent.directory.v1.GetAppDownloadInfoResponse
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.ListAppListingsRequest
import app.accrescent.directory.v1.ListAppListingsResponse
import io.quarkus.grpc.GrpcService
import io.smallrye.mutiny.Uni

@GrpcService
class DirectoryServiceImpl : DirectoryService {
    override fun createApp(request: CreateAppRequest): Uni<CreateAppResponse> {
        TODO("Not yet implemented")
    }

    override fun getAppListing(request: GetAppListingRequest): Uni<GetAppListingResponse> {
        TODO("Not yet implemented")
    }

    override fun listAppListings(request: ListAppListingsRequest): Uni<ListAppListingsResponse> {
        TODO("Not yet implemented")
    }

    override fun getAppDownloadInfo(request: GetAppDownloadInfoRequest): Uni<GetAppDownloadInfoResponse> {
        TODO("Not yet implemented")
    }
}
