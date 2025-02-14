// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.internal.v1.App
import app.accrescent.directory.internal.v1.CreateAppRequest
import app.accrescent.directory.internal.v1.CreateAppResponse
import app.accrescent.directory.internal.v1.ObjectMetadata
import app.accrescent.directory.v1.AppDownloadInfo
import app.accrescent.directory.v1.AppListing
import app.accrescent.directory.v1.CompatibilityLevel
import app.accrescent.directory.v1.DeviceAttributes
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.GetAppDownloadInfoRequest
import app.accrescent.directory.v1.GetAppDownloadInfoResponse
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.Image
import app.accrescent.directory.v1.ReleaseChannel
import app.accrescent.directory.v1.SplitDownloadInfo
import app.accrescent.services.directory.data.AppRepository
import com.google.protobuf.TextFormat
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.quarkus.grpc.GrpcClient
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.vertx.RunOnVertxContext
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.stream.Stream
import app.accrescent.directory.internal.v1.DirectoryService as InternalDirectoryService

private const val REQUEST_TIMEOUT_SECS: Long = 5

@QuarkusTest
class DirectoryServicesImplTest {
    @GrpcClient
    lateinit var internal: InternalDirectoryService

    @GrpcClient
    lateinit var external: DirectoryService

    @Inject
    private lateinit var appRepository: AppRepository

    @ConfigProperty(name = "artifacts.base-url")
    private lateinit var artifactsBaseUrl: String

    @BeforeEach
    @RunOnVertxContext
    fun beforeEach(asserter: TransactionalUniAsserter) {
        asserter.execute(Supplier { cleanDatabase() })
    }

    @WithTransaction
    fun cleanDatabase(): Uni<Long> {
        return appRepository.deleteAll()
    }

    @ParameterizedTest
    @MethodSource("generateParamsForCreateAppValidatesRequest")
    fun createAppValidatesRequest(request: CreateAppRequest) {
        val response = CompletableFuture<Status.Code>()

        internal.createApp(request).subscribe().with(
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
    fun createAppWithValidRequestReturnsApp() {
        val createdApp = CompletableFuture<App?>()

        internal.createApp(validCreateAppRequest).subscribe().with(
            { createdApp.complete(it.app) },
            {
                createdApp.complete(null)
            }
        )

        assertEquals(
            validCreateAppRequest.app,
            createdApp.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS),
        )
    }

    @Test
    fun createAppCanBeCalledTwiceWithSameRequest() {
        val firstResponse = CompletableFuture<CreateAppResponse?>()
        val secondResponse = CompletableFuture<CreateAppResponse?>()

        internal.createApp(validCreateAppRequest)
            .onFailure()
            .invoke(Runnable { firstResponse.complete(null) })
            .chain { response ->
                firstResponse.complete(response)
                internal.createApp(validCreateAppRequest)
            }
            .subscribe()
            .with(
                { secondResponse.complete(it) },
                { secondResponse.complete(null) },
            )

        assertEquals(
            validCreateAppRequest.app,
            firstResponse.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)?.app,
        )
        assertEquals(
            validCreateAppRequest.app,
            secondResponse.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)?.app,
        )
    }

    @Test
    fun getAppListingRequiresAppId() {
        val status = CompletableFuture<Status.Code>()

        val request = validGetAppListingRequest.toBuilder().clearAppId().build()

        internal.createApp(validCreateAppRequest)
            .chain { -> external.getAppListing(request) }
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

        external.getAppListing(validGetAppListingRequest)
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
    @MethodSource("generateParamsForGetAppListingWithFullRequestReturnsExpectedListing")
    fun getAppListingWithFullRequestReturnsExpectedListing(
        expectedAppListing: AppListing,
        request: GetAppListingRequest,
    ) {
        val responseFuture = CompletableFuture<GetAppListingResponse?>()

        internal.createApp(validCreateAppRequest)
            .chain { -> external.getAppListing(request) }
            .subscribe()
            .with(
                { responseFuture.complete(it) },
                { responseFuture.complete(null) },
            )

        val response = responseFuture.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        requireNotNull(response)
        assertGetAppListingResponseRequiredFieldsMatchExpected(expectedAppListing, response)
        assertGetAppListingResponseCompatibilityMatchesExpected(response)
    }

    @Test
    fun getAppListingWithoutDeviceAttributesReturnsOnlyExpectedFields() {
        val responseFuture = CompletableFuture<GetAppListingResponse?>()

        val request = validGetAppListingRequest.toBuilder().clearDeviceAttributes().build()

        internal.createApp(validCreateAppRequest)
            .chain { -> external.getAppListing(request) }
            .subscribe()
            .with(
                { responseFuture.complete(it) },
                { responseFuture.complete(null) },
            )

        val response = responseFuture.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        requireNotNull(response)
        assertGetAppListingResponseRequiredFieldsMatchExpected(expectedFullAppListingEn, response)
        assert(!response.listing.hasCompatibility())
    }

    @ParameterizedTest
    @MethodSource("generateParamsForGetAppListingsWithoutReleaseChannelReturnsExpectedListing")
    fun getAppListingsWithoutReleaseChannelReturnsExpectedListing(request: GetAppListingRequest) {
        val responseFuture = CompletableFuture<GetAppListingResponse?>()

        internal.createApp(validCreateAppRequest)
            .chain { -> external.getAppListing(request) }
            .subscribe()
            .with(
                { responseFuture.complete(it) },
                { responseFuture.complete(null) },
            )

        val response = responseFuture.get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        requireNotNull(response)
        assertGetAppListingResponseRequiredFieldsMatchExpected(expectedFullAppListingEn, response)
        assertGetAppListingResponseCompatibilityMatchesExpected(response)
    }

    @ParameterizedTest
    @MethodSource("generateParamsForGetAppDownloadInfoValidatesRequest")
    fun getAppDownloadInfoValidatesRequest(request: GetAppDownloadInfoRequest) {
        val response = CompletableFuture<Status.Code>()

        external.getAppDownloadInfo(request).subscribe().with(
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
        val response = internal.createApp(validCreateAppRequest)
            .chain { -> external.getAppDownloadInfo(validGetAppDownloadInfoRequest) }
            .subscribe()
            .asCompletionStage()
            .get(REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        assertEquals(getExpectedAppDownloadInfoResponse(), response)
    }

    private fun getExpectedAppDownloadInfoResponse() = GetAppDownloadInfoResponse.newBuilder()
        .setAppDownloadInfo(
            AppDownloadInfo.newBuilder()
                .setDownloadSize(4648720)
                .addAllSplitDownloadInfo(
                    listOf(
                        SplitDownloadInfo.newBuilder()
                            .setDownloadSize(44897)
                            .setUrl("$artifactsBaseUrl/a4f60d94-e402-475d-9e6e-f4585ef13da2")
                            .build(),
                        SplitDownloadInfo.newBuilder()
                            .setDownloadSize(4558309)
                            .setUrl("$artifactsBaseUrl/38119a8c-1163-4c7d-89c6-cc5c902a6ca1")
                            .build(),
                        SplitDownloadInfo.newBuilder()
                            .setDownloadSize(45514)
                            .setUrl("$artifactsBaseUrl/d24e0b69-a011-42ed-835e-17d1557fd10a")
                            .build(),
                    ),
                ),
        )
        .build()

    companion object {
        private val validCreateAppRequest = javaClass.classLoader
            .getResourceAsStream("valid-create-app-request.txtpb")!!
            .use {
                val builder = CreateAppRequest.newBuilder()
                it.reader().use { TextFormat.merge(it, builder) }
                builder
            }
            .build()

        // Retrieved from an Android 15 Pixel 9 x86_64 emulator using `bundletool get-device-spec`
        // and `adb shell getprop`.
        //
        // Has all known fields set as of bundletool 1.18.0 except device_tier, device_groups, and
        // country_set.
        private val validDeviceAttributes = javaClass.classLoader
            .getResourceAsStream("valid-device-attributes.txtpb")!!
            .use {
                val builder = DeviceAttributes.newBuilder()
                it.reader().use { TextFormat.merge(it, builder) }
                builder
            }
            .build()

        private val validGetAppListingRequest: GetAppListingRequest = GetAppListingRequest.newBuilder()
            .setAppId("app.accrescent.client")
            .setDeviceAttributes(validDeviceAttributes)
            .setReleaseChannel(
                ReleaseChannel.newBuilder()
                    .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_STABLE),
            )
            .build()

        private val validGetAppDownloadInfoRequest = GetAppDownloadInfoRequest.newBuilder()
            .setAppId("app.accrescent.client")
            .setDeviceAttributes(validDeviceAttributes)
            .setReleaseChannel(
                ReleaseChannel.newBuilder().setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_STABLE)
            )
            .build()

        private val expectedFullAppListingEn = AppListing.newBuilder()
            .setLanguage("en")
            .setName("Accrescent")
            .setShortDescription("A private and secure Android app store")
            .setIcon(
                // The URL here is arbitrary and can be assumed to change at any time. What's
                // necessary is that it is populated (and of course a valid URL).
                Image.newBuilder()
                    .setUrl("https://not.real.cdn/file/57297a7-6f2c-4a04-9656-497af21bf6b2"),
            )
            .setVersionName("0.25.0")
            .build()
        private val expectedFullAppListingDe = AppListing.newBuilder()
            .setLanguage("de")
            .setName("Accrescent")
            .setShortDescription("Ein privater und sicherer Android App Store")
            .setIcon(
                Image.newBuilder()
                    .setUrl("https://not.real.cdn/file/57297a7-6f2c-4a04-9656-497af21bf6b2"),
            )
            .setVersionName("0.25.0")
            .build()

        private fun assertGetAppListingResponseRequiredFieldsMatchExpected(
            expectedListing: AppListing,
            response: GetAppListingResponse,
        ) {
            assert(response.hasListing())
            assertEquals(expectedListing.language, response.listing.language)
            assertEquals(expectedListing.name, response.listing.name)
            assertEquals(expectedListing.shortDescription, response.listing.shortDescription)
            assert(response.listing.hasIcon())
            assert(response.listing.icon.hasUrl())
            assertEquals(expectedListing.versionName, response.listing.versionName)
        }

        private fun assertGetAppListingResponseCompatibilityMatchesExpected(
            response: GetAppListingResponse,
        ) {
            assert(response.listing.hasCompatibility())
            assert(response.listing.compatibility.hasLevel())
            assertEquals(
                CompatibilityLevel.COMPATIBILITY_LEVEL_COMPATIBLE,
                response.listing.compatibility.level,
            )
        }

        @JvmStatic
        fun generateParamsForCreateAppValidatesRequest(): Stream<CreateAppRequest> {
            return Stream.of(
                // Missing the app ID
                validCreateAppRequest.toBuilder().clearAppId().build(),
                // Missing the app metadata
                validCreateAppRequest.toBuilder().clearApp().build(),
                // Missing the default listing language
                validCreateAppRequest.toBuilder()
                    .apply { appBuilder.clearDefaultListingLanguage() }
                    .build(),
                // Listing list doesn't contain the default listing language
                validCreateAppRequest.toBuilder()
                    .apply {
                        appBuilder.removeListings(app.listingsList.indexOfFirst {
                            it.language == app.defaultListingLanguage
                        })
                    }
                    .build(),
                // Listing contains duplicate languages
                validCreateAppRequest.toBuilder()
                    .apply { appBuilder.addListings(validCreateAppRequest.app.listingsList[0]) }
                    .build(),
                // Listings don't have a language set
                validCreateAppRequest.toBuilder()
                    .apply { appBuilder.listingsBuilderList.forEach { it.clearLanguage() } }
                    .build(),
                // Listings don't have a name set
                validCreateAppRequest.toBuilder()
                    .apply { appBuilder.listingsBuilderList.forEach { it.clearName() } }
                    .build(),
                // Listings don't have a short description set
                validCreateAppRequest.toBuilder()
                    .apply { appBuilder.listingsBuilderList.forEach { it.clearShortDescription() } }
                    .build(),
                // Listings don't have an icon set
                validCreateAppRequest.toBuilder()
                    .apply { appBuilder.listingsBuilderList.forEach { it.clearIcon() } }
                    .build(),
                // Listing icons don't have an object ID set
                validCreateAppRequest.toBuilder()
                    .apply {
                        appBuilder.listingsBuilderList.forEach { it.iconBuilder.clearObjectId() }
                    }
                    .build(),
                // Package metadata doesn't exist for the stable release channel
                validCreateAppRequest.toBuilder()
                    .apply {
                        appBuilder.removePackageMetadata(app.packageMetadataList.indexOfFirst {
                            it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
                        })
                    }
                    .build(),
                // Package metadata contains duplicate release channels
                validCreateAppRequest.toBuilder()
                    .apply {
                        appBuilder
                            .addPackageMetadata(validCreateAppRequest.app.packageMetadataList[0])
                    }
                    .build(),
                // Package metadata is missing metadata for an object
                validCreateAppRequest.toBuilder()
                    .apply {
                        appBuilder.packageMetadataBuilderList[0].packageMetadataBuilder
                            .removeObjectMetadata("38119a8c-1163-4c7d-89c6-cc5c902a6ca1")
                    }
                    .build(),
                // An object's metadata doesn't have uncompressed size set
                validCreateAppRequest.toBuilder()
                    .apply {
                        val packageMetadataBuilder = appBuilder
                            .packageMetadataBuilderList[0]
                            .packageMetadataBuilder
                        packageMetadataBuilder.putObjectMetadata(
                            "38119a8c-1163-4c7d-89c6-cc5c902a6ca1",
                            packageMetadataBuilder
                                .objectMetadataMap["38119a8c-1163-4c7d-89c6-cc5c902a6ca1"]!!
                                .toBuilder()
                                .clearUncompressedSize()
                                .build()
                        )
                    }
                    .build(),
                // Object metadata is specified for an object not found in the build-apks result
                validCreateAppRequest.toBuilder()
                    .apply {
                        appBuilder.packageMetadataBuilderList[0].packageMetadataBuilder
                            .putObjectMetadata(
                                "nonexistent-object",
                                ObjectMetadata.newBuilder().setUncompressedSize(4096).build()
                            )
                    }
                    .build(),
            )
        }

        @JvmStatic
        fun generateParamsForGetAppListingWithFullRequestReturnsExpectedListing()
                : Stream<Arguments> {
            return Stream.of(
                // With the "en-US" locale, expected to fall back to the "en" listing
                Arguments.of(expectedFullAppListingEn, validGetAppListingRequest),
                // With the "en" locale, expected to choose the "en" listing
                Arguments.of(
                    expectedFullAppListingEn,
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
                    expectedFullAppListingDe,
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
                    expectedFullAppListingDe,
                    validGetAppListingRequest.toBuilder()
                        .apply {
                            deviceAttributesBuilder.specBuilder
                                .clearSupportedLocales()
                                .addAllSupportedLocales(listOf("de", "en"))
                        }
                        .build(),
                ),
            )
        }

        @JvmStatic
        fun generateParamsForGetAppListingsWithoutReleaseChannelReturnsExpectedListing()
                : Stream<GetAppListingRequest> {
            return Stream.of(
                // Without release channel field specified
                validGetAppListingRequest.toBuilder().clearReleaseChannel().build(),
                // Without release channel's well known field specified
                validGetAppListingRequest.toBuilder()
                    .setReleaseChannel(ReleaseChannel.getDefaultInstance())
                    .build(),
                // With the unspecified well known release channel value
                validGetAppListingRequest.toBuilder()
                    .apply {
                        releaseChannelBuilder
                            .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED)
                    }
                    .build(),
            )
        }

        @JvmStatic
        fun generateParamsForGetAppDownloadInfoValidatesRequest()
                : Stream<GetAppDownloadInfoRequest> {
            return Stream.of(
                // Missing the app ID
                validGetAppDownloadInfoRequest.toBuilder().clearAppId().build(),
                // Missing device attributes
                validGetAppDownloadInfoRequest.toBuilder().clearDeviceAttributes().build(),
            )
        }
    }
}
