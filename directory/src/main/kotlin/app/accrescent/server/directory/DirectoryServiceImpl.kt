// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.bundletool.android.bundle.Commands
import app.accrescent.directory.priv.v1.ListAppListingsPageToken
import app.accrescent.directory.priv.v1.listAppListingsPageToken
import app.accrescent.directory.v1.AppListingView
import app.accrescent.directory.v1.CompatibilityLevel
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.GetAppDownloadInfoRequest
import app.accrescent.directory.v1.GetAppDownloadInfoResponse
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.GetUpdateInfoRequest
import app.accrescent.directory.v1.GetUpdateInfoResponse
import app.accrescent.directory.v1.ListAppListingsRequest
import app.accrescent.directory.v1.ListAppListingsResponse
import app.accrescent.directory.v1.appDownloadInfo
import app.accrescent.directory.v1.appListing
import app.accrescent.directory.v1.compatibility
import app.accrescent.directory.v1.downloadSize
import app.accrescent.directory.v1.getAppDownloadInfoResponse
import app.accrescent.directory.v1.getAppListingResponse
import app.accrescent.directory.v1.getUpdateInfoResponse
import app.accrescent.directory.v1.image
import app.accrescent.directory.v1.listAppListingsResponse
import app.accrescent.directory.v1.splitDownloadInfo
import app.accrescent.directory.v1.updateInfo
import app.accrescent.server.directory.data.App
import app.accrescent.server.directory.data.AppDefaultListingLanguage
import app.accrescent.server.directory.data.Listing
import app.accrescent.server.directory.data.ListingId
import app.accrescent.server.directory.data.ReleaseChannel
import app.accrescent.server.directory.data.StorageObject
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 200u

/**
 * The server implementation of [DirectoryService]
 */
@GrpcService
class DirectoryServiceImpl @Inject constructor(
    @ConfigProperty(name = "artifacts.base-url")
    private val artifactsBaseUrl: String,
) : DirectoryService {
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

                val appListing = appListing {
                    appId = listing.id.appId
                    language = listing.id.language
                    name = listing.name
                    shortDescription = listing.shortDescription
                    icon = image { url = "${artifactsBaseUrl}/${listing.icon.objectId}" }
                    versionName = releaseChannel.versionName

                    if (!request.hasDeviceAttributes()) return@appListing

                    val buildApksResult = try {
                        Commands.BuildApksResult.parseFrom(releaseChannel.buildApksResult)
                    } catch (_: InvalidProtocolBufferException) {
                        throw Status.fromCode(Status.Code.INTERNAL)
                            .withDescription("stored BuildApksResult is not a valid message")
                            .asRuntimeException()
                    }

                    val matchingApkObjectIds =
                        getMatchingApkObjectIds(buildApksResult, request.deviceAttributes).toSet()

                    val compatibilityLevel = if (matchingApkObjectIds.isNotEmpty()) {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                    } else {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_INCOMPATIBLE
                    }
                    compatibility = compatibility { level = compatibilityLevel }

                    if (compatibilityLevel == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE) {
                        downloadSize = downloadSize {
                            uncompressedTotal = releaseChannel.objects
                                .filter { matchingApkObjectIds.contains(it.id) }
                                .sumOf { it.uncompressedSize }
                                .toInt()
                        }
                    }
                }

                getAppListingResponse { this.listing = appListing }
            }

        return response
    }

    @WithTransaction
    override fun listAppListings(request: ListAppListingsRequest): Uni<ListAppListingsResponse> {
        // Coerce and validate request parameters
        val pageSize = if (request.hasPageSize() && request.pageSize != 0) {
            request.pageSize.toUInt().coerceAtMost(MAX_PAGE_SIZE)
        } else {
            DEFAULT_PAGE_SIZE
        }
        val skip = if (request.hasSkip()) request.skip.toUInt() else 0u
        val pageToken = if (request.hasPageToken()) {
            try {
                val tokenBytes = Base64.getUrlDecoder().decode(request.pageToken)
                val token = ListAppListingsPageToken.parseFrom(tokenBytes)
                if (!token.hasLastAppId()) {
                    throw generateInvalidPageTokenError().asRuntimeException()
                }

                token
            } catch (_: IllegalArgumentException) {
                throw generateInvalidPageTokenError().asRuntimeException()
            } catch (_: InvalidProtocolBufferException) {
                throw generateInvalidPageTokenError().asRuntimeException()
            }
        } else {
            null
        }

        // Find matching app listings with the following steps:
        //
        // 1. Fetch (appId, defaultListingLanguage) pairs according to the request query parameters.
        // 2. For each app ID returned in step 1, fetch all languages the app has a listing for.
        // 3. Calculate the best matching listing ID for each app based on the results of steps 1
        //    and 2.
        // 4. Fetch all (listing, stable release channel metadata) tuples matching the listing IDs
        //    obtained in step 3.
        // 5. Filter the results to only apps compatible with the provided device if device
        //    attributes were provided.
        val response = App.findDefaultListingLanguagesByQuery(
            pageSize,
            skip,
            pageToken?.lastAppId,
        ).map {
            it.associateBy(
                AppDefaultListingLanguage::id,
                AppDefaultListingLanguage::defaultListingLanguage,
            )
        }.chain { appDefaultListingLanguages ->
            Listing.findIdsForApps(appDefaultListingLanguages.keys).map {
                it.groupBy(ListingId::appId, ListingId::language)
                    .map {
                        val defaultListingLanguage = appDefaultListingLanguages[it.key]
                            ?: throw Status.fromCode(Status.Code.INTERNAL)
                                .withDescription("default listing language expected for queried app ID")
                                .asRuntimeException()
                        it.key to calculateBestListingLanguageMatch(
                            defaultListingLanguage,
                            it.value,
                            request.deviceAttributes.spec.supportedLocalesList,
                        )
                    }
            }
        }.chain { bestMatchingLanguages ->
            Listing.findWithStableMetadataByIds(bestMatchingLanguages)
        }.map {
            val listings = it.map { (listing, releaseChannel) ->
                appListing {
                    appId = listing.id.appId
                    language = listing.id.language
                    name = listing.name
                    shortDescription = listing.shortDescription
                    icon = image { url = "${artifactsBaseUrl}/${listing.icon.objectId}" }

                    if (request.hasView() && request.view == AppListingView.APP_LISTING_VIEW_FULL) {
                        versionName = releaseChannel.versionName
                    }

                    if (!request.hasDeviceAttributes()) return@appListing

                    val buildApksResult = try {
                        Commands.BuildApksResult.parseFrom(releaseChannel.buildApksResult)
                    } catch (_: InvalidProtocolBufferException) {
                        throw Status.fromCode(Status.Code.INTERNAL)
                            .withDescription("stored BuildApksResult is not a valid message")
                            .asRuntimeException()
                    }

                    val matchingApkObjectIds =
                        getMatchingApkObjectIds(buildApksResult, request.deviceAttributes).toSet()

                    val compatibilityLevel = if (matchingApkObjectIds.isNotEmpty()) {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                    } else {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_INCOMPATIBLE
                    }
                    compatibility = compatibility { level = compatibilityLevel }

                    if (
                        request.view == AppListingView.APP_LISTING_VIEW_FULL &&
                        compatibilityLevel == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                    ) {
                        downloadSize = downloadSize {
                            uncompressedTotal = releaseChannel.objects
                                .filter { matchingApkObjectIds.contains(it.id) }
                                .sumOf { it.uncompressedSize }
                                .toInt()
                        }
                    }
                }
            }.filter {
                !it.hasCompatibility() ||
                        it.compatibility.level == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
            }

            if (listings.isNotEmpty()) {
                // Set a page token indicating that there may be more results
                val pageToken = listAppListingsPageToken { lastAppId = listings.last().appId }
                val encodedPageToken = Base64.getUrlEncoder().encodeToString(pageToken.toByteArray())

                listAppListingsResponse {
                    this.listings.addAll(listings)
                    nextPageToken = encodedPageToken
                }
            } else {
                listAppListingsResponse {}
            }
        }

        return response
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

            getAppDownloadInfoResponse {
                appDownloadInfo = appDownloadInfo {
                    downloadSize = totalDownloadSize.toInt()
                    splitDownloadInfo.addAll(storageObjects.map {
                        splitDownloadInfo {
                            downloadSize = it.uncompressedSize.toInt()
                            url = "${artifactsBaseUrl}/${it.id}"
                        }
                    })
                }
            }
        }

        return response
    }

    @WithTransaction
    override fun getUpdateInfo(request: GetUpdateInfoRequest): Uni<GetUpdateInfoResponse> {
        if (!request.hasAppId()) {
            throw Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("app ID is missing but required")
                .asRuntimeException()
        } else if (!request.hasBaseVersionCode()) {
            throw Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("base version code is missing but required")
                .asRuntimeException()
        }

        val response = ReleaseChannel.findByAppIdAndName(
            request.appId,
            request.releaseChannel.canonicalForm(),
        ).map { releaseChannel ->
            if (releaseChannel == null) {
                throw Status.fromCode(Status.Code.NOT_FOUND)
                    .withDescription("provided app ID or release channel does not exist")
                    .asRuntimeException()
            }

            val updateIsAvailable = releaseChannel.versionCode > request.baseVersionCode.toUInt()

            val response = getUpdateInfoResponse {
                if (!updateIsAvailable) return@getUpdateInfoResponse

                updateInfo = updateInfo {
                    if (!request.hasDeviceAttributes()) return@updateInfo

                    val buildApksResult = try {
                        Commands.BuildApksResult.parseFrom(releaseChannel.buildApksResult)
                    } catch (_: InvalidProtocolBufferException) {
                        throw Status.fromCode(Status.Code.INTERNAL)
                            .withDescription("stored BuildApksResult is not a valid message")
                            .asRuntimeException()
                    }

                    val matchingApkObjectIds =
                        getMatchingApkObjectIds(buildApksResult, request.deviceAttributes)

                    val compatibilityLevel = if (matchingApkObjectIds.isNotEmpty()) {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                    } else {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_INCOMPATIBLE
                    }

                    compatibility = compatibility { level = compatibilityLevel }
                }
            }

            response
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

    /**
     * Calculates the best listing language match based on the user's preferred languages
     *
     * The best-match algorithm used is naive and simply loops through the user's preferred
     * languages looking for a match among the app's available listing languages. If no exact match
     * is found, the app's default listing is returned.
     *
     * This process could likely be improved by adopting either RFC 4647 lookup or
     * [Android's resource resolution strategy](https://developer.android.com/guide/topics/resources/multilingual-support#postN).
     *
     * All language parameters are BCP-47 tags.
     *
     * @param defaultLanguage the app's default language to use as a fallback
     * @param listingLanguages the available listing languages for the app
     * @param userPreferredLanguages the user's preferred languages in descending order of
     * preference
     */
    private fun calculateBestListingLanguageMatch(
        defaultLanguage: String,
        listingLanguages: List<String>,
        userPreferredLanguages: List<String>,
    ): String {
        val listingLanguages = listingLanguages.toSet()

        for (preferredLanguage in userPreferredLanguages) {
            if (listingLanguages.contains(preferredLanguage)) {
                return preferredLanguage
            }
        }

        return defaultLanguage
    }

    private companion object {
        private fun generateInvalidPageTokenError() = Status.fromCode(Status.Code.INVALID_ARGUMENT)
            .withDescription("provided page token is invalid")
    }
}
