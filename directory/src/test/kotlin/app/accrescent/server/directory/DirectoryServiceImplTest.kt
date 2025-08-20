// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.directory.priv.v1.listAppListingsPageToken
import app.accrescent.directory.v1.AppListing
import app.accrescent.directory.v1.AppListingView
import app.accrescent.directory.v1.CompatibilityLevel
import app.accrescent.directory.v1.DeviceAttributes
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.GetAppDownloadInfoRequest
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.GetUpdateInfoRequest
import app.accrescent.directory.v1.appDownloadInfo
import app.accrescent.directory.v1.appListing
import app.accrescent.directory.v1.compatibility
import app.accrescent.directory.v1.copy
import app.accrescent.directory.v1.downloadSize
import app.accrescent.directory.v1.getAppDownloadInfoRequest
import app.accrescent.directory.v1.getAppDownloadInfoResponse
import app.accrescent.directory.v1.getAppListingRequest
import app.accrescent.directory.v1.getUpdateInfoRequest
import app.accrescent.directory.v1.image
import app.accrescent.directory.v1.listAppListingsRequest
import app.accrescent.directory.v1.releaseChannel
import app.accrescent.directory.v1.splitDownloadInfo
import app.accrescent.server.directory.data.AppRepository
import com.google.protobuf.TextFormat
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.quarkus.grpc.GrpcClient
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.kafka.KafkaCompanionResource
import io.quarkus.test.vertx.RunOnVertxContext
import io.smallrye.mutiny.Uni
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.stream.Stream
import app.accrescent.directory.v1.ReleaseChannel as DirectoryReleaseChannel

private const val REQUEST_TIMEOUT_SECS: Long = 5

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource::class)
class DirectoryServiceImplTest {
    @InjectKafkaCompanion
    lateinit var kafka: KafkaCompanion

    @GrpcClient
    lateinit var directory: DirectoryService

    @Inject
    private lateinit var appRepository: AppRepository

    @ConfigProperty(name = "artifacts.base-url")
    private lateinit var artifactsBaseUrl: String

    @BeforeEach
    @RunOnVertxContext
    fun cleanDatabase(asserter: TransactionalUniAsserter) {
        asserter.execute(Supplier { cleanDatabase() })
    }

    @WithTransaction
    fun cleanDatabase(): Uni<Long> {
        return appRepository.deleteAll()
    }

    @BeforeEach
    fun registerKafkaSerdes() {
        KafkaHelper.registerSerdes(kafka)
    }

    @Test
    fun getAppListingRequiresAppId() {
        val status = CompletableFuture<Status.Code>()

        val request = validGetAppListingRequest.copy { clearAppId() }

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        directory.getAppListing(request)
            .subscribe()
            .with(
                { status.complete(Status.Code.OK) },
                {
                    require(it is StatusRuntimeException)
                    status.complete(it.status.code)
                },
            )

        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            status.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS),
        )
    }

    @Test
    fun getAppListingForUnknownAppIdReturnsNotFound() {
        val status = CompletableFuture<Status.Code>()

        directory.getAppListing(validGetAppListingRequest)
            .subscribe()
            .with(
                { status.complete(Status.Code.OK) },
                {
                    require(it is StatusRuntimeException)
                    status.complete(it.status.code)
                },
            )

        assertEquals(Status.Code.NOT_FOUND, status.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS))
    }

    @ParameterizedTest
    @MethodSource("generateParamsForGetAppListingReturnsExpectedListing")
    fun getAppListingReturnsExpectedListing(
        expectedAppListing: AppListing,
        request: GetAppListingRequest,
    ) {
        KafkaHelper.publishApps(
            kafka,
            TestDataHelper.validAppPublicationRequested,
            TestDataHelper.validAppPublicationRequested2,
            TestDataHelper.validAppPublicationRequested3Incompatible,
        )

        val response = directory.getAppListing(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        assertGetAppListingResponseMatchesExpected(expectedAppListing, response)
    }

    @Test
    fun listAppListingsWithBasicViewReturnsRequiredFields() {
        val request = listAppListingsRequest { view = AppListingView.APP_LISTING_VIEW_BASIC }

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val listing = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            .listingsList
            .first()

        assertTrue(listing.hasAppId())
        assertTrue(listing.hasLanguage())
        assertTrue(listing.hasName())
        assertTrue(listing.hasShortDescription())
        assertTrue(listing.hasIcon())
    }

    @Test
    fun listAppListingsWithFullViewReturnsAllFields() {
        val request = listAppListingsRequest { view = AppListingView.APP_LISTING_VIEW_FULL }

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val listing = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            .listingsList
            .first()

        assertTrue(listing.hasAppId())
        assertTrue(listing.hasLanguage())
        assertTrue(listing.hasName())
        assertTrue(listing.hasShortDescription())
        assertTrue(listing.hasIcon())
        assertTrue(listing.hasVersionName())
    }

    @Test
    fun listAppListingsWithDeviceAttributesReturnsCompatibility() {
        val request = listAppListingsRequest { deviceAttributes = validDeviceAttributes }

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val listing = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            .listingsList
            .first()

        assertTrue(listing.hasCompatibility())
    }

    @Test
    fun listAppListingsWithFullViewAndCompatibleDeviceAttributesReturnsValidDownloadSize() {
        val request = listAppListingsRequest {
            view = AppListingView.APP_LISTING_VIEW_FULL
            deviceAttributes = validDeviceAttributes
        }

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val listing = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            .listingsList
            .first()

        assertTrue(listing.hasDownloadSize())
        assertTrue(listing.downloadSize.hasUncompressedTotal())
    }

    @Test
    fun listAppListingsReturnsEmptySetAndNoPageTokenWhenSkipOvershoots() {
        val request = listAppListingsRequest { skip = Int.MAX_VALUE }

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val response = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        assertTrue(response.listingsList.isEmpty())
        assertFalse(response.hasNextPageToken())
    }

    @Test
    fun listAppListingsReturnsPageTokenWhenItemsRemain() {
        val listAppListingsRequest = listAppListingsRequest { pageSize = 1 }

        KafkaHelper.publishApps(
            kafka,
            TestDataHelper.validAppPublicationRequested,
            TestDataHelper.validAppPublicationRequested2,
        )

        val response = directory.listAppListings(listAppListingsRequest)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        assertTrue(response.hasNextPageToken())
    }

    @Test
    fun listAppListingsWithPageTokenTraversesAll() {
        val request = listAppListingsRequest { pageSize = 1 }

        val accumulatedListings = mutableListOf<AppListing>()

        KafkaHelper.publishApps(
            kafka,
            TestDataHelper.validAppPublicationRequested,
            TestDataHelper.validAppPublicationRequested2,
        )

        var nextPageToken: String? = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            .let {
                accumulatedListings.addAll(it.listingsList)
                assertTrue(it.hasNextPageToken())
                it.nextPageToken
            }
        while (nextPageToken != null) {
            val nextRequest = request.copy { pageToken = nextPageToken }
            val nextResponse = directory.listAppListings(nextRequest)
                .subscribeAsCompletionStage()
                .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

            accumulatedListings.addAll(nextResponse.listingsList)

            nextPageToken = if (nextResponse.hasNextPageToken()) {
                nextResponse.nextPageToken
            } else {
                null
            }
        }

        assertTrue(
            accumulatedListings.map { it.appId }
                .containsAll(listOf("app.accrescent.client", "com.none.tom.exiferaser")),
        )
    }

    // We assume here that a single default request will return enough listings for this test to be
    // useful, which is not necessarily the case since it's not guaranteed by the API. However, this
    // is how the API currently behaves, so it should be fine unless we change that behavior.
    @Test
    fun listAppListingsReturnsOnlyCompatibleApps() {
        val request = listAppListingsRequest { deviceAttributes = validDeviceAttributes }

        KafkaHelper.publishApps(
            kafka,
            TestDataHelper.validAppPublicationRequested,
            TestDataHelper.validAppPublicationRequested2,
            TestDataHelper.validAppPublicationRequested3Incompatible,
        )

        val response = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        for (listing in response.listingsList) {
            assertTrue(
                listing.compatibility.level == CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE,
            )
        }
    }

    @Test
    fun listAppListingsTraversalReturnsNoDuplicates() {
        val request = listAppListingsRequest {}

        val accumulatedListings = mutableListOf<AppListing>()

        KafkaHelper.publishApps(
            kafka,
            TestDataHelper.validAppPublicationRequested,
            TestDataHelper.validAppPublicationRequested2,
            TestDataHelper.validAppPublicationRequested3Incompatible,
        )

        var nextPageToken: String? = directory.listAppListings(request)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            .let {
                accumulatedListings.addAll(it.listingsList)
                it.nextPageToken
            }
        while (nextPageToken != null) {
            val nextRequest = request.copy { pageToken = nextPageToken }
            val nextResponse = directory.listAppListings(nextRequest)
                .subscribeAsCompletionStage()
                .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

            accumulatedListings.addAll(nextResponse.listingsList)

            nextPageToken = if (nextResponse.hasNextPageToken()) {
                nextResponse.nextPageToken
            } else {
                null
            }
        }

        assertEquals(accumulatedListings.distinctBy { it.appId }.size, accumulatedListings.size)
    }

    @ParameterizedTest
    @MethodSource("generateParamsForListAppListingsRejectsInvalidPageToken")
    fun listAppListingsRejectsInvalidPageToken(pageToken: String) {
        val response = CompletableFuture<Status.Code>()

        val request = listAppListingsRequest { this.pageToken = pageToken }

        directory.listAppListings(request)
            .subscribe()
            .with(
                { response.complete(Status.Code.OK) },
                {
                    require(it is StatusRuntimeException)
                    response.complete(it.status.code)
                }
            )

        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            response.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS),
        )
    }

    @ParameterizedTest
    @MethodSource("generateParamsForGetAppDownloadInfoValidatesRequest")
    fun getAppDownloadInfoValidatesRequest(request: GetAppDownloadInfoRequest) {
        val response = CompletableFuture<Status.Code>()

        directory.getAppDownloadInfo(request).subscribe().with(
            { response.complete(Status.Code.OK) },
            {
                require(it is StatusRuntimeException)
                response.complete(it.status.code)
            },
        )

        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            response.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS),
        )
    }

    @Test
    fun getAppDownloadInfoReturnsExpected() {
        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val response = directory.getAppDownloadInfo(validGetAppDownloadInfoRequest)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        assertEquals(getExpectedAppDownloadInfoResponse(), response)
    }

    @ParameterizedTest
    @MethodSource("generateParamsForGetUpdateInfoValidatesRequest")
    fun getUpdateInfoValidatesRequest(request: GetUpdateInfoRequest) {
        val status = CompletableFuture<Status.Code>()

        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        directory.getUpdateInfo(request)
            .subscribe()
            .with(
                { status.complete(Status.Code.OK) },
                {
                    require(it is StatusRuntimeException)
                    status.complete(it.status.code)
                },
            )

        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            status.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS),
        )
    }

    @Test
    fun getUpdateInfoForUnknownAppReturnsNotFound() {
        val status = CompletableFuture<Status.Code>()

        directory.getUpdateInfo(validGetUpdateInfoRequest)
            .subscribe()
            .with(
                { status.complete(Status.Code.OK) },
                {
                    require(it is StatusRuntimeException)
                    status.complete(it.status.code)
                },
            )

        assertEquals(Status.Code.NOT_FOUND, status.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS))
    }

    @Test
    fun getUpdateInfoWithDeviceAttributesReturnsCompatibility() {
        KafkaHelper.publishApps(kafka, TestDataHelper.validAppPublicationRequested)

        val response = directory.getUpdateInfo(validGetUpdateInfoRequest)
            .subscribeAsCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        assertTrue(response.updateInfo.hasCompatibility())
    }

    private fun getExpectedAppDownloadInfoResponse() = getAppDownloadInfoResponse {
        appDownloadInfo = appDownloadInfo {
            downloadSize = 4648720
            splitDownloadInfo.addAll(
                listOf(
                    splitDownloadInfo {
                        downloadSize = 44897
                        url = "$artifactsBaseUrl/a4f60d94-e402-475d-9e6e-f4585ef13da2"
                    },
                    splitDownloadInfo {
                        downloadSize = 4558309
                        url = "$artifactsBaseUrl/38119a8c-1163-4c7d-89c6-cc5c902a6ca1"
                    },
                    splitDownloadInfo {
                        downloadSize = 45514
                        url = "$artifactsBaseUrl/d24e0b69-a011-42ed-835e-17d1557fd10a"
                    },
                )
            )
        }
    }

    companion object {
        // Retrieved from an Android 15 Pixel 9 x86_64 emulator using `bundletool get-device-spec`
        // and `adb shell getprop`.
        //
        // Has all known fields set as of bundletool 1.18.0 except device_tier, device_groups, and
        // country_set.
        private val validDeviceAttributes = DirectoryServiceImplTest::class.java.classLoader
            .getResourceAsStream("valid-device-attributes.txtpb")!!
            .use {
                val builder = DeviceAttributes.newBuilder()
                it.reader().use { TextFormat.merge(it, builder) }
                builder
            }
            .build()

        private val validGetAppListingRequest = getAppListingRequest {
            appId = "app.accrescent.client"
            deviceAttributes = validDeviceAttributes
            releaseChannel = releaseChannel {
                wellKnown = DirectoryReleaseChannel.WellKnown.WELL_KNOWN_STABLE
            }
        }

        private val validGetAppDownloadInfoRequest = getAppDownloadInfoRequest {
            appId = "app.accrescent.client"
            deviceAttributes = validDeviceAttributes
            releaseChannel = releaseChannel {
                wellKnown = DirectoryReleaseChannel.WellKnown.WELL_KNOWN_STABLE
            }
        }

        private val validGetUpdateInfoRequest = getUpdateInfoRequest {
            appId = "app.accrescent.client"
            baseVersionCode = 48
            deviceAttributes = validDeviceAttributes
            releaseChannel = releaseChannel {
                wellKnown = DirectoryReleaseChannel.WellKnown.WELL_KNOWN_STABLE
            }
        }

        private val expectedFullAppListingAccrescentEn = appListing {
            appId = "app.accrescent.client"
            language = "en"
            name = "Accrescent"
            shortDescription = "A private and secure Android app store"
            icon = image {
                // The URL here is arbitrary and can be assumed to change at any time. What's
                // necessary is that it is populated (and of course a valid URL).
                url = "https://not.real.cdn/file/57297a7-6f2c-4a04-9656-497af21bf6b2"
            }
            versionName = "0.25.0"
            compatibility = compatibility {
                level = CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
            }
            downloadSize = downloadSize { uncompressedTotal = 4648720 }
        }
        private val expectedFullAppListingAccrescentDe = appListing {
            appId = "app.accrescent.client"
            language = "de"
            name = "Accrescent"
            shortDescription = "Ein privater und sicherer Android App Store"
            icon = image { url = "https://not.real.cdn/file/57297a7-6f2c-4a04-9656-497af21bf6b2" }
            versionName = "0.25.0"
            compatibility = compatibility {
                level = CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
            }
            downloadSize = downloadSize { uncompressedTotal = 4648720 }
        }
        private val expectedFullAppListingExifEraserEn = appListing {
            appId = "com.none.tom.exiferaser"
            language = "en"
            name = "ExifEraser"
            shortDescription = "Permissionless image metadata erasing application for Android"
            icon = image { url = "https://not.real.cdn/file/57297a7-6f2c-4a04-9656-497af21bf6b2" }
            versionName = "6.3.0"
            compatibility = compatibility {
                level = CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE
            }
            downloadSize = downloadSize { uncompressedTotal = 3849948 }
        }

        private fun assertGetAppListingResponseMatchesExpected(
            expectedListing: AppListing,
            response: GetAppListingResponse,
        ) {
            assertTrue(response.hasListing())
            assertEquals(expectedListing.appId, response.listing.appId)
            assertEquals(expectedListing.language, response.listing.language)
            assertEquals(expectedListing.name, response.listing.name)
            assertEquals(expectedListing.shortDescription, response.listing.shortDescription)
            assertTrue(response.listing.hasIcon())
            assertTrue(response.listing.icon.hasUrl())
            assertEquals(expectedListing.versionName, response.listing.versionName)
            assertEquals(expectedListing.hasCompatibility(), response.listing.hasCompatibility())
            if (expectedListing.hasCompatibility()) {
                assertEquals(expectedListing.compatibility, response.listing.compatibility)
            }
            if (expectedListing.hasDownloadSize()) {
                assertEquals(expectedListing.downloadSize, response.listing.downloadSize)
            }
        }

        @JvmStatic
        fun generateParamsForGetAppListingReturnsExpectedListing()
                : Stream<Arguments> {
            return Stream.of(
                // With the "en-US" locale, expected to fall back to the "en" listing
                Arguments.of(expectedFullAppListingAccrescentEn, validGetAppListingRequest),
                // With the "en" locale, expected to choose the "en" listing
                Arguments.of(
                    expectedFullAppListingAccrescentEn,
                    validGetAppListingRequest.toBuilder()
                        .apply {
                            deviceAttributesBuilder.specBuilder
                                .clearSupportedLocales()
                                .addSupportedLocales("en")
                        }
                        .build(),
                ),
                // With the "de" locale, expected to choose the "de" listing
                Arguments.of(
                    expectedFullAppListingAccrescentDe,
                    validGetAppListingRequest.toBuilder()
                        .apply {
                            deviceAttributesBuilder.specBuilder
                                .clearSupportedLocales()
                                .addSupportedLocales("de")
                        }
                        .build(),
                ),
                // With the "de" and "en" locales in that order, expected to choose the "de" listing
                Arguments.of(
                    expectedFullAppListingAccrescentDe,
                    validGetAppListingRequest.toBuilder()
                        .apply {
                            deviceAttributesBuilder.specBuilder
                                .clearSupportedLocales()
                                .addAllSupportedLocales(listOf("de", "en"))
                        }
                        .build(),
                ),
                // Without device attributes specified, expected to not set the compatibility or
                // download size fields
                Arguments.of(
                    expectedFullAppListingAccrescentEn.copy {
                        clearCompatibility()
                        clearDownloadSize()
                    },
                    validGetAppListingRequest.copy { clearDeviceAttributes() },
                ),
                // Without release channel field specified, expected to return the listing for the
                // stable channel
                Arguments.of(
                    expectedFullAppListingAccrescentEn,
                    validGetAppListingRequest.copy { clearReleaseChannel() },
                ),
                // Without the release channel's well known field specified, expected to return the
                // listing for the stable channel
                Arguments.of(
                    expectedFullAppListingAccrescentEn,
                    validGetAppListingRequest.copy { releaseChannel = releaseChannel {} }
                ),
                // With the unspecified well known release channel value, expected to return the
                // listing for the stable channel
                Arguments.of(
                    expectedFullAppListingAccrescentEn,
                    validGetAppListingRequest.copy {
                        releaseChannel = releaseChannel.copy {
                            wellKnown = DirectoryReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED
                        }
                    },
                ),
                // Without support for a required ABI, expected to return an incompatible listing
                Arguments.of(
                    expectedFullAppListingAccrescentEn.copy {
                        compatibility = compatibility {
                            level = CompatibilityLevel.COMPATIBILITY_LEVEL_INCOMPATIBLE
                        }
                        clearDownloadSize()
                    },
                    validGetAppListingRequest.toBuilder()
                        .apply { deviceAttributesBuilder.specBuilder.clearSupportedAbis() }
                        .build(),
                ),
                // With a different requested app ID
                Arguments.of(
                    expectedFullAppListingExifEraserEn,
                    validGetAppListingRequest.copy { appId = "com.none.tom.exiferaser" },
                ),
            )
        }

        @JvmStatic
        fun generateParamsForListAppListingsRejectsInvalidPageToken(): Stream<String> {
            return Stream.of(
                // Invalid base64url
                "invalidbase64url?",
                // Valid base64url, but invalid ListAppListingsPageToken protobuf
                "base64url",
                // Valid ListAppListingsPageToken protobuf, but missing the last_app_id field
                Base64.getUrlEncoder().encodeToString(listAppListingsPageToken {}.toByteArray()),
            )
        }

        @JvmStatic
        fun generateParamsForGetAppDownloadInfoValidatesRequest()
                : Stream<GetAppDownloadInfoRequest> {
            return Stream.of(
                // Missing the app ID
                validGetAppDownloadInfoRequest.copy { clearAppId() },
                // Missing device attributes
                validGetAppDownloadInfoRequest.copy { clearDeviceAttributes() },
            )
        }

        @JvmStatic
        fun generateParamsForGetUpdateInfoValidatesRequest(): Stream<GetUpdateInfoRequest> {
            return Stream.of(
                // Missing the app ID
                validGetUpdateInfoRequest.copy { clearAppId() },
                // Missing the base version code
                validGetUpdateInfoRequest.copy { clearBaseVersionCode() },
            )
        }
    }
}
