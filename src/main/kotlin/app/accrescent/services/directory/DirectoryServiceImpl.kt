// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.v1.AppDownloadInfo
import app.accrescent.directory.v1.AppListing
import app.accrescent.directory.v1.Compatibility
import app.accrescent.directory.v1.CompatibilityLevel
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.GetAppDownloadInfoRequest
import app.accrescent.directory.v1.GetAppDownloadInfoResponse
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.Image
import app.accrescent.directory.v1.ListAppListingsRequest
import app.accrescent.directory.v1.ListAppListingsResponse
import app.accrescent.directory.v1.SplitDownloadInfo
import app.accrescent.services.directory.data.Listing
import app.accrescent.services.directory.data.ReleaseChannel
import app.accrescent.services.directory.data.StorageObject
import com.android.bundle.Commands
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * The server implementation of [DirectoryService]
 */
@GrpcService
class DirectoryServiceImpl : DirectoryService {
    @ConfigProperty(name = "artifacts.base-url")
    private lateinit var artifactsBaseUrl: String

    @WithTransaction
    override fun getAppListing(request: GetAppListingRequest): Uni<GetAppListingResponse> {
        if (!request.hasAppId()) {
            throw Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("app ID is missing but required")
                .asRuntimeException()
        }

        val bestMatchingListing = findBestListingMatch(
            request.appId,
            request.deviceAttributes.spec.supportedLocalesList,
        )
        val releaseChannel = ReleaseChannel.findByAppIdAndName(
            request.appId,
            request.releaseChannel.canonicalForm(),
        )

        val response = Uni.combine()
            .all()
            .unis(bestMatchingListing, releaseChannel)
            .usingConcurrencyOf(1)
            .with {
                val listing = it[0] as Listing?
                val releaseChannel = it[1] as ReleaseChannel?

                when {
                    listing == null && releaseChannel == null -> throw Status
                        .fromCode(Status.Code.NOT_FOUND)
                        .withDescription("app with ID ${request.appId} not found")
                        .asRuntimeException()

                    listing == null -> throw Status.fromCode(Status.Code.INTERNAL)
                        .withDescription("app with ID ${request.appId} has no listings")
                        .asRuntimeException()

                    releaseChannel == null -> throw Status.fromCode(Status.Code.INTERNAL)
                        .withDescription("app with ID ${request.appId} has no release channels")
                        .asRuntimeException()
                }

                val listingBuilder = AppListing.newBuilder()
                    .setLanguage(listing.language)
                    .setName(listing.name)
                    .setShortDescription(listing.shortDescription)
                    .setIcon(
                        Image.newBuilder()
                            .setUrl("${artifactsBaseUrl}/${listing.icon.objectId}"),
                    )
                    .setVersionName(releaseChannel.versionName)

                if (request.hasDeviceAttributes()) {
                    val buildApksResult = try {
                        Commands.BuildApksResult.parseFrom(releaseChannel.buildApksResult)
                    } catch (_: InvalidProtocolBufferException) {
                        throw Status.fromCode(Status.Code.INTERNAL)
                            .withDescription("stored BuildApksResult is not a valid message")
                            .asRuntimeException()
                    }

                    listingBuilder.setCompatibility(
                        Compatibility.newBuilder()
                            .setLevel(
                                if (appSupportsDevice(buildApksResult, request.deviceAttributes)) {
                                    CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                                } else {
                                    CompatibilityLevel.COMPATIBILITY_LEVEL_INCOMPATIBLE
                                }
                            ),
                    )
                }

                GetAppListingResponse.newBuilder().setListing(listingBuilder).build()
            }

        return response
    }

    override fun listAppListings(request: ListAppListingsRequest): Uni<ListAppListingsResponse> {
        throw Status.fromCode(Status.Code.UNIMPLEMENTED).asRuntimeException()
    }

    @WithTransaction
    override fun getAppDownloadInfo(
        request: GetAppDownloadInfoRequest,
    ): Uni<GetAppDownloadInfoResponse> {
        if (!request.hasAppId()) {
            throw Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("app ID is missing but required")
                .asRuntimeException()
        } else if (!request.hasDeviceAttributes()) {
            throw Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("device attributes are missing but required")
                .asRuntimeException()
        }

        val response = ReleaseChannel.findBuildApksResult(
            request.appId,
            request.releaseChannel.canonicalForm(),
        ).map {
            if (it == null) {
                throw Status.fromCode(Status.Code.NOT_FOUND)
                    .withDescription("no info matching the provided app and release channel found")
                    .asRuntimeException()
            }

            val buildApksResult = try {
                Commands.BuildApksResult.parseFrom(it.buildApksResult)
            } catch (_: InvalidProtocolBufferException) {
                throw Status.fromCode(Status.Code.INTERNAL)
                    .withDescription("stored BuildApksResult is not a valid message")
                    .asRuntimeException()
            }

            val matchingApkObjectIds =
                getMatchingApkObjectIds(buildApksResult, request.deviceAttributes)

            if (matchingApkObjectIds.isEmpty()) {
                throw Status.fromCode(Status.Code.NOT_FOUND)
                    .withDescription("no download information matches the provided device attributes")
                    .asRuntimeException()
            }

            matchingApkObjectIds
        }.chain { ids ->
            StorageObject.findByIds(ids)
        }.map { storageObjects ->
            if (storageObjects.isEmpty()) {
                throw Status.fromCode(Status.Code.INTERNAL)
                    .withDescription("referenced storage object not found in database")
                    .asRuntimeException()
            }

            val totalDownloadSize = storageObjects.sumOf { it.uncompressedSize }

            GetAppDownloadInfoResponse.newBuilder()
                .setAppDownloadInfo(
                    AppDownloadInfo.newBuilder()
                        .setDownloadSize(totalDownloadSize.toInt())
                        .addAllSplitDownloadInfo(
                            storageObjects.map {
                                SplitDownloadInfo.newBuilder()
                                    .setDownloadSize(it.uncompressedSize.toInt())
                                    .setUrl("${artifactsBaseUrl}/${it.id}")
                                    .build()
                            },
                        ),
                )
                .build()
        }

        return response
    }

    /**
     * Finds the best app listing match based on the user's preferred languages
     *
     * The best-match algorithm used is naive and simply loops through the user's preferred
     * languages looking for a match among the app's available listing languages. If no exact match
     * is found, the app's default listing is returned.
     *
     * This process could likely be improved by adopting either RFC 4647 lookup or
     * [Android's resource resolution strategy](https://developer.android.com/guide/topics/resources/multilingual-support#postN).
     *
     * @param appId the ID of the app to find a listing for
     * @param userPreferredLanguages the user's preferred languages as BCP-47 tags in descending
     * order of preference
     */
    private fun findBestListingMatch(
        appId: String,
        userPreferredLanguages: List<String>,
    ): Uni<Listing?> {
        return Listing.findAllLanguagesForApp(appId).chain { listingLanguages ->
            val listingLanguages = listingLanguages.map { it.language }.toSet()

            for (preferredLanguage in userPreferredLanguages) {
                if (listingLanguages.contains(preferredLanguage)) {
                    return@chain Listing.findByAppIdAndLanguage(appId, preferredLanguage)
                }
            }

            Listing.findDefaultForApp(appId)
        }
    }
}
