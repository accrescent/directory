// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.internal.v1.ListAppListingsPageToken
import app.accrescent.directory.v1.AppDownloadInfo
import app.accrescent.directory.v1.AppListing
import app.accrescent.directory.v1.AppListingView
import app.accrescent.directory.v1.Compatibility
import app.accrescent.directory.v1.CompatibilityLevel
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.DownloadSize
import app.accrescent.directory.v1.GetAppDownloadInfoRequest
import app.accrescent.directory.v1.GetAppDownloadInfoResponse
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.GetUpdateInfoRequest
import app.accrescent.directory.v1.GetUpdateInfoResponse
import app.accrescent.directory.v1.Image
import app.accrescent.directory.v1.ListAppListingsRequest
import app.accrescent.directory.v1.ListAppListingsResponse
import app.accrescent.directory.v1.SplitDownloadInfo
import app.accrescent.directory.v1.UpdateInfo
import app.accrescent.services.directory.data.App
import app.accrescent.services.directory.data.AppDefaultListingLanguage
import app.accrescent.services.directory.data.Listing
import app.accrescent.services.directory.data.ListingId
import app.accrescent.services.directory.data.ReleaseChannel
import app.accrescent.services.directory.data.StorageObject
import app.accrescent.services.directory.data.events.Download
import app.accrescent.services.directory.data.events.DownloadType
import app.accrescent.services.directory.data.events.EventRepository
import app.accrescent.services.directory.data.events.ListingView
import app.accrescent.services.directory.data.events.UpdateCheck
import com.android.bundle.Commands
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.grpc.RegisterInterceptor
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

private const val DEFAULT_PAGE_SIZE = 50u
private const val MAX_PAGE_SIZE = 200u

/**
 * The server implementation of [DirectoryService]
 */
@GrpcService
@RegisterInterceptor(RegionMetadataAttacherInterceptor::class)
class DirectoryServiceImpl @Inject constructor(
    private val eventRepository: EventRepository,
) : DirectoryService {
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
                    .setAppId(listing.id.appId)
                    .setLanguage(listing.id.language)
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

                    val matchingApkObjectIds =
                        getMatchingApkObjectIds(buildApksResult, request.deviceAttributes).toSet()

                    val compatibilityLevel = if (matchingApkObjectIds.isNotEmpty()) {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                    } else {
                        CompatibilityLevel.COMPATIBILITY_LEVEL_INCOMPATIBLE
                    }
                    listingBuilder.setCompatibility(
                        Compatibility.newBuilder().setLevel(compatibilityLevel)
                    )

                    if (compatibilityLevel == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE) {
                        val totalUncompressedSize = releaseChannel.objects
                            .filter { matchingApkObjectIds.contains(it.id) }
                            .sumOf { it.uncompressedSize }
                        val downloadSize = DownloadSize.newBuilder()
                            .setUncompressedTotal(totalUncompressedSize.toInt())
                        listingBuilder.setDownloadSize(downloadSize)
                    }
                }

                eventRepository.addListingView(
                    ListingView(
                        date = LocalDate.now(ZoneOffset.UTC),
                        appId = request.appId,
                        languageCode = listing.id.language,
                        deviceSdkVersion = request.deviceAttributes.spec.sdkVersion.toUInt(),
                        countryCode = GEO_REGION_CONTEXT_KEY.get(),
                    )
                )

                GetAppListingResponse.newBuilder().setListing(listingBuilder).build()
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
                val listingBuilder = AppListing.newBuilder()
                    .setAppId(listing.id.appId)
                    .setLanguage(listing.id.language)
                    .setName(listing.name)
                    .setShortDescription(listing.shortDescription)
                    .setIcon(
                        Image.newBuilder()
                            .setUrl("${artifactsBaseUrl}/${listing.icon.objectId}"),
                    )

                if (request.hasView() && request.view == AppListingView.APP_LISTING_VIEW_FULL) {
                    listingBuilder.setVersionName(releaseChannel.versionName)
                }

                if (request.hasDeviceAttributes()) {
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
                    listingBuilder.setCompatibility(
                        Compatibility.newBuilder().setLevel(compatibilityLevel)
                    )

                    if (
                        request.view == AppListingView.APP_LISTING_VIEW_FULL &&
                        compatibilityLevel == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
                    ) {
                        val totalUncompressedSize = releaseChannel.objects
                            .filter { matchingApkObjectIds.contains(it.id) }
                            .sumOf { it.uncompressedSize }
                        val downloadSize = DownloadSize.newBuilder()
                            .setUncompressedTotal(totalUncompressedSize.toInt())
                        listingBuilder.setDownloadSize(downloadSize)
                    }
                }

                listingBuilder.build()
            }.filter {
                !it.hasCompatibility() ||
                        it.compatibility.level == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
            }

            if (listings.isNotEmpty()) {
                // Set a page token indicating that there may be more results
                val pageToken = ListAppListingsPageToken.newBuilder()
                    .setLastAppId(listings.last().appId)
                    .build()
                val encodedPageToken = Base64.getUrlEncoder().encodeToString(pageToken.toByteArray())

                ListAppListingsResponse.newBuilder()
                    .addAllListings(listings)
                    .setNextPageToken(encodedPageToken)
                    .build()
            } else {
                ListAppListingsResponse.getDefaultInstance()
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

            eventRepository.addDownload(
                Download(
                    date = LocalDate.now(ZoneOffset.UTC),
                    appId = request.appId,
                    versionCode = storageObjects[0].releaseChannel.versionCode,
                    downloadType = if (request.hasBaseVersionCode()) {
                        DownloadType.UPDATE
                    } else {
                        DownloadType.INITIAL
                    },
                    deviceSdkVersion = request.deviceAttributes.spec.sdkVersion.toUInt(),
                    countryCode = GEO_REGION_CONTEXT_KEY.get(),
                )
            )

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

            val responseBuilder = GetUpdateInfoResponse.newBuilder()

            val updateIsAvailable = releaseChannel.versionCode > request.baseVersionCode.toUInt()
            if (updateIsAvailable) {
                val updateInfoBuilder = UpdateInfo.newBuilder()

                if (request.hasDeviceAttributes()) {
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
                    updateInfoBuilder.setCompatibility(
                        Compatibility.newBuilder().setLevel(compatibilityLevel)
                    )
                }

                responseBuilder.setUpdateInfo(updateInfoBuilder)
            }

            eventRepository.addUpdateCheck(
                UpdateCheck(
                    date = LocalDate.now(ZoneOffset.UTC),
                    appId = request.appId,
                    releaseChannel = request.releaseChannel.canonicalForm(),
                    deviceSdkVersion = request.deviceAttributes.spec.sdkVersion.toUInt(),
                    countryCode = GEO_REGION_CONTEXT_KEY.get(),
                )
            )

            responseBuilder.build()
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
