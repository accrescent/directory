// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.internal.v1.App
import app.accrescent.directory.internal.v1.App.PackageMetadataEntry
import app.accrescent.directory.internal.v1.CreateAppRequest
import app.accrescent.directory.internal.v1.CreateAppResponse
import app.accrescent.directory.internal.v1.PackageMetadata
import app.accrescent.directory.v1.AppListing
import app.accrescent.directory.v1.CompatibilityLevel
import app.accrescent.directory.v1.DeviceAttributes
import app.accrescent.directory.v1.DirectoryService
import app.accrescent.directory.v1.GetAppListingRequest
import app.accrescent.directory.v1.GetAppListingResponse
import app.accrescent.directory.v1.Image
import app.accrescent.directory.v1.ReleaseChannel
import app.accrescent.services.directory.data.AppRepository
import com.android.bundle.Commands
import com.android.bundle.Devices
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.quarkus.grpc.GrpcClient
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.vertx.RunOnVertxContext
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
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
import app.accrescent.directory.internal.v1.AppListing as InternalAppListing
import app.accrescent.directory.internal.v1.DirectoryService as InternalDirectoryService
import app.accrescent.directory.internal.v1.Image as InternalImage

private const val REQUEST_TIMEOUT_SECS: Long = 5

@QuarkusTest
class DirectoryServicesImplTest {
    @GrpcClient
    lateinit var internal: InternalDirectoryService

    @GrpcClient
    lateinit var external: DirectoryService

    @Inject
    private lateinit var appRepository: AppRepository

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

        val request = GetAppListingRequest.newBuilder()
            .mergeFrom(validGetAppListingRequest)
            .clearAppId()
            .build()

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

        val request = GetAppListingRequest.newBuilder()
            .mergeFrom(validGetAppListingRequest)
            .clearDeviceAttributes()
            .build()

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

    companion object {
        private val validCreateAppRequest: CreateAppRequest = CreateAppRequest.newBuilder()
            .setAppId("app.accrescent.client")
            .setApp(
                App.newBuilder()
                    .setDefaultListingLanguage("en")
                    .addAllListings(
                        listOf(
                            InternalAppListing.newBuilder()
                                .setLanguage("en")
                                .setName("Accrescent")
                                .setShortDescription("A private and secure Android app store")
                                .setIcon(
                                    InternalImage.newBuilder()
                                        .setObjectId("57297a7-6f2c-4a04-9656-497af21bf6b2")
                                )
                                .build(),
                            InternalAppListing.newBuilder()
                                .setLanguage("de")
                                .setName("Accrescent")
                                .setShortDescription("Ein privater und sicherer Android App Store")
                                .setIcon(
                                    InternalImage.newBuilder()
                                        .setObjectId("57297a7-6f2c-4a04-9656-497af21bf6b2")
                                )
                                .build(),
                        ),
                    )
                    .addPackageMetadata(
                        PackageMetadataEntry.newBuilder()
                            .setReleaseChannel(
                                ReleaseChannel.newBuilder()
                                    .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_STABLE)
                            )
                            .setPackageMetadata(
                                PackageMetadata.newBuilder()
                                    .setVersionCode(49)
                                    .setVersionName("0.25.0")
                                    .setBuildApksResult(
                                        Commands.BuildApksResult.newBuilder()
                                            .setPackageName("app.accrescent.client")
                                    )
                            ),
                    )
                    .build()
            )
            .build()

        // Retrieved from an Android 15 Pixel 9 x86_64 emulator using `bundletool get-device-spec`
        // and `adb shell getprop`.
        //
        // Has all known fields set as of bundletool 1.18.0 except device_tier, device_groups, and
        // country_set.
        private val validGetAppListingRequest: GetAppListingRequest = GetAppListingRequest.newBuilder()
            .setAppId("app.accrescent.client")
            .setDeviceAttributes(
                DeviceAttributes.newBuilder()
                    .setSpec(
                        Devices.DeviceSpec.newBuilder()
                            .addAllSupportedAbis(listOf("x86_64", "arm64-v8a"))
                            .addSupportedLocales("en-US")
                            .addAllDeviceFeatures(
                                listOf(
                                    "reqGlEsVersion=0x30000",
                                    "android.hardware.audio.output",
                                    "android.hardware.biometrics.face",
                                    "android.hardware.bluetooth",
                                    "android.hardware.bluetooth_le",
                                    "android.hardware.camera",
                                    "android.hardware.camera.any",
                                    "android.hardware.camera.autofocus",
                                    "android.hardware.camera.capability.manual_post_processing",
                                    "android.hardware.camera.capability.manual_sensor",
                                    "android.hardware.camera.capability.raw",
                                    "android.hardware.camera.concurrent",
                                    "android.hardware.camera.flash",
                                    "android.hardware.camera.front",
                                    "android.hardware.camera.level.full",
                                    "android.hardware.faketouch",
                                    "android.hardware.fingerprint",
                                    "android.hardware.hardware_keystore=300",
                                    "android.hardware.identity_credential=202301",
                                    "android.hardware.keystore.app_attest_key",
                                    "android.hardware.location",
                                    "android.hardware.location.gps",
                                    "android.hardware.location.network",
                                    "android.hardware.microphone",
                                    "android.hardware.ram.normal",
                                    "android.hardware.reboot_escrow",
                                    "android.hardware.screen.landscape",
                                    "android.hardware.screen.portrait",
                                    "android.hardware.security.model.compatible",
                                    "android.hardware.sensor.accelerometer",
                                    "android.hardware.sensor.ambient_temperature",
                                    "android.hardware.sensor.barometer",
                                    "android.hardware.sensor.compass",
                                    "android.hardware.sensor.gyroscope",
                                    "android.hardware.sensor.light",
                                    "android.hardware.sensor.proximity",
                                    "android.hardware.sensor.relative_humidity",
                                    "android.hardware.telephony",
                                    "android.hardware.telephony.calling",
                                    "android.hardware.telephony.data",
                                    "android.hardware.telephony.gsm",
                                    "android.hardware.telephony.ims",
                                    "android.hardware.telephony.messaging",
                                    "android.hardware.telephony.radio.access",
                                    "android.hardware.telephony.subscription",
                                    "android.hardware.touchscreen",
                                    "android.hardware.touchscreen.multitouch",
                                    "android.hardware.touchscreen.multitouch.distinct",
                                    "android.hardware.touchscreen.multitouch.jazzhand",
                                    "android.hardware.vulkan.compute",
                                    "android.hardware.vulkan.level=1",
                                    "android.hardware.vulkan.version=4206592",
                                    "android.hardware.wifi",
                                    "android.hardware.wifi.direct",
                                    "android.hardware.wifi.passpoint",
                                    "android.software.activities_on_secondary_displays",
                                    "android.software.adoptable_storage",
                                    "android.software.app_enumeration",
                                    "android.software.app_widgets",
                                    "android.software.autofill",
                                    "android.software.backup",
                                    "android.software.cant_save_state",
                                    "android.software.companion_device_setup",
                                    "android.software.controls",
                                    "android.software.credentials",
                                    "android.software.cts",
                                    "android.software.device_admin",
                                    "android.software.device_lock",
                                    "android.software.erofs",
                                    "android.software.file_based_encryption",
                                    "android.software.home_screen",
                                    "android.software.incremental_delivery=2",
                                    "android.software.input_methods",
                                    "android.software.ipsec_tunnel_migration",
                                    "android.software.ipsec_tunnels",
                                    "android.software.live_wallpaper",
                                    "android.software.managed_users",
                                    "android.software.midi",
                                    "android.software.opengles.deqp.level=132645633",
                                    "android.software.picture_in_picture",
                                    "android.software.print",
                                    "android.software.secure_lock_screen",
                                    "android.software.securely_removes_users",
                                    "android.software.telecom",
                                    "android.software.verified_boot",
                                    "android.software.virtualization_framework",
                                    "android.software.voice_recognizers",
                                    "android.software.vulkan.deqp.level=132645633",
                                    "android.software.webview",
                                    "android.software.window_magnification",
                                    "com.google.android.apps.dialer.SUPPORTED",
                                    "com.google.android.feature.EXCHANGE_6_2",
                                    "com.google.android.feature.GOOGLE_BUILD",
                                    "com.google.android.feature.GOOGLE_EXPERIENCE",
                                    "com.google.android.feature.PERSONAL_SAFETY",
                                    "com.google.android.feature.WELLBEING",
                                ),
                            )
                            .addAllGlExtensions(
                                listOf(
                                    "GL_EXT_debug_marker",
                                    "GL_EXT_robustness",
                                    "GL_OES_EGL_sync",
                                    "GL_OES_EGL_image",
                                    "GL_OES_EGL_image_external",
                                    "GL_OES_depth24",
                                    "GL_OES_depth32",
                                    "GL_OES_element_index_uint",
                                    "GL_OES_texture_float",
                                    "GL_OES_texture_float_linear",
                                    "GL_OES_compressed_paletted_texture",
                                    "GL_OES_compressed_ETC1_RGB8_texture",
                                    "GL_OES_depth_texture",
                                    "GL_OES_texture_half_float",
                                    "GL_OES_texture_half_float_linear",
                                    "GL_OES_packed_depth_stencil",
                                    "GL_OES_vertex_half_float",
                                    "GL_OES_standard_derivatives",
                                    "GL_OES_texture_npot",
                                    "GL_OES_rgb8_rgba8",
                                    "GL_EXT_color_buffer_float",
                                    "GL_EXT_color_buffer_half_float",
                                    "GL_EXT_texture_format_BGRA8888",
                                    "GL_APPLE_texture_format_BGRA8888",
                                    "ANDROID_EMU_CHECKSUM_HELPER_v1",
                                    "ANDROID_EMU_native_sync_v2",
                                    "ANDROID_EMU_dma_v1",
                                    "ANDROID_EMU_direct_mem",
                                    "ANDROID_EMU_host_composition_v1",
                                    "ANDROID_EMU_host_composition_v2",
                                    "ANDROID_EMU_vulkan",
                                    "ANDROID_EMU_deferred_vulkan_commands",
                                    "ANDROID_EMU_vulkan_null_optional_strings",
                                    "ANDROID_EMU_vulkan_create_resources_with_requirements",
                                    "ANDROID_EMU_YUV_Cache",
                                    "ANDROID_EMU_vulkan_ignored_handles",
                                    "ANDROID_EMU_has_shared_slots_host_memory_allocator",
                                    "ANDROID_EMU_vulkan_free_memory_sync",
                                    "ANDROID_EMU_vulkan_shader_float16_int8",
                                    "ANDROID_EMU_vulkan_async_queue_submit",
                                    "ANDROID_EMU_vulkan_queue_submit_with_commands",
                                    "ANDROID_EMU_sync_buffer_data",
                                    "ANDROID_EMU_vulkan_async_qsri",
                                    "ANDROID_EMU_read_color_buffer_dma",
                                    "ANDROID_EMU_hwc_multi_configs",
                                    "GL_OES_EGL_image_external_essl3",
                                    "GL_OES_vertex_array_object",
                                    "GL_KHR_texture_compression_astc_ldr",
                                    "ANDROID_EMU_host_side_tracing",
                                    "ANDROID_EMU_gles_max_version_3_0",
                                ),
                            )
                            .setScreenDensity(420)
                            .setSdkVersion(35)
                            .setCodename("REL")
                            .setSdkRuntime(Devices.SdkRuntime.newBuilder().setSupported(true))
                            .setRamBytes(2067869696)
                            .setBuildBrand("google")
                            .setBuildDevice("emu64xa")
                            .setSocManufacturer("AOSP")
                            .setSocModel("ranchu")
                            .build()
                    )
                    .build()
            )
            .setReleaseChannel(
                ReleaseChannel.newBuilder()
                    .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_STABLE),
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
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .clearAppId()
                    .build(),
                // Missing the app metadata
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .clearApp()
                    .build(),
                // Missing the default listing language
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .clearDefaultListingLanguage()
                            .build()
                    )
                    .build(),
                // Listing list doesn't contain the default listing language
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .apply {
                                removeListings(listingsList.indexOfFirst {
                                    it.language == defaultListingLanguage
                                })
                            }
                            .build()
                    )
                    .build(),
                // Listing contains duplicate languages
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .addListings(validCreateAppRequest.app.listingsList[0])
                    )
                    .build(),
                // Listings don't have a language set
                CreateAppRequest
                    .newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .clearListings()
                            .addAllListings(validCreateAppRequest.app.listingsList.map {
                                InternalAppListing
                                    .newBuilder()
                                    .mergeFrom(it)
                                    .clearLanguage()
                                    .build()
                            })
                            .build()
                    ).build(),
                // Listings don't have a name set
                CreateAppRequest
                    .newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .clearListings()
                            .addAllListings(validCreateAppRequest.app.listingsList.map {
                                InternalAppListing
                                    .newBuilder()
                                    .mergeFrom(it)
                                    .clearName()
                                    .build()
                            })
                            .build()
                    ).build(),
                // Listings don't have a short description set
                CreateAppRequest
                    .newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .clearListings()
                            .addAllListings(validCreateAppRequest.app.listingsList.map {
                                InternalAppListing
                                    .newBuilder()
                                    .mergeFrom(it)
                                    .clearShortDescription()
                                    .build()
                            })
                            .build()
                    )
                    .build(),
                // Listings don't have an icon set
                CreateAppRequest
                    .newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .clearListings()
                            .addAllListings(validCreateAppRequest.app.listingsList.map {
                                InternalAppListing
                                    .newBuilder()
                                    .mergeFrom(it)
                                    .clearIcon()
                                    .build()
                            })
                            .build()
                    )
                    .build(),
                // Listing icons don't have an object ID set
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .clearListings()
                            .addAllListings(validCreateAppRequest.app.listingsList.map {
                                InternalAppListing
                                    .newBuilder()
                                    .mergeFrom(it)
                                    .apply {
                                        icon = InternalImage
                                            .newBuilder()
                                            .mergeFrom(icon)
                                            .clearObjectId()
                                            .build()
                                    }
                                    .build()
                            })
                            .build()
                    ).build(),
                // Package metadata doesn't exist for the stable release channel
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .apply {
                                removePackageMetadata(packageMetadataList.indexOfFirst {
                                    it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
                                })
                            }
                            .build()
                    )
                    .build(),
                // Package metadata contains duplicate release channels
                CreateAppRequest.newBuilder()
                    .mergeFrom(validCreateAppRequest)
                    .setApp(
                        App.newBuilder()
                            .mergeFrom(validCreateAppRequest.app)
                            .addPackageMetadata(
                                PackageMetadataEntry.newBuilder()
                                    .mergeFrom(validCreateAppRequest.app.packageMetadataList[0])
                                    .build()
                            )
                    )
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
                    GetAppListingRequest.newBuilder()
                        .mergeFrom(validGetAppListingRequest)
                        .setDeviceAttributes(
                            DeviceAttributes.newBuilder()
                                .mergeFrom(validGetAppListingRequest.deviceAttributes)
                                .setSpec(
                                    Devices.DeviceSpec.newBuilder()
                                        .mergeFrom(validGetAppListingRequest.deviceAttributes.spec)
                                        .clearSupportedLocales()
                                        .addSupportedLocales("en")
                                )
                        )
                        .build(),
                ),
                // With the "de" locale, expected to choose the "de" listing
                Arguments.of(
                    expectedFullAppListingDe,
                    GetAppListingRequest.newBuilder()
                        .mergeFrom(validGetAppListingRequest)
                        .setDeviceAttributes(
                            DeviceAttributes.newBuilder()
                                .mergeFrom(validGetAppListingRequest.deviceAttributes)
                                .setSpec(
                                    Devices.DeviceSpec.newBuilder()
                                        .mergeFrom(validGetAppListingRequest.deviceAttributes.spec)
                                        .clearSupportedLocales()
                                        .addSupportedLocales("de")
                                )
                        )
                        .build(),
                ),
                // With the "de" and "en" locales in that order, expected to choose the "de" listing
                Arguments.of(
                    expectedFullAppListingDe,
                    GetAppListingRequest.newBuilder()
                        .mergeFrom(validGetAppListingRequest)
                        .setDeviceAttributes(
                            DeviceAttributes.newBuilder()
                                .mergeFrom(validGetAppListingRequest.deviceAttributes)
                                .setSpec(
                                    Devices.DeviceSpec.newBuilder()
                                        .mergeFrom(validGetAppListingRequest.deviceAttributes.spec)
                                        .clearSupportedLocales()
                                        .addAllSupportedLocales(listOf("de", "en"))
                                )
                        )
                        .build(),
                ),
            )
        }

        @JvmStatic
        fun generateParamsForGetAppListingsWithoutReleaseChannelReturnsExpectedListing()
                : Stream<GetAppListingRequest> {
            return Stream.of(
                // Without release channel field specified
                GetAppListingRequest.newBuilder()
                    .mergeFrom(validGetAppListingRequest)
                    .clearReleaseChannel()
                    .build(),
                // Without release channel's well known field specified
                GetAppListingRequest.newBuilder()
                    .mergeFrom(validGetAppListingRequest)
                    .setReleaseChannel(ReleaseChannel.newBuilder().build())
                    .build(),
                // With the unspecified well known release channel value
                GetAppListingRequest.newBuilder()
                    .mergeFrom(validGetAppListingRequest)
                    .setReleaseChannel(
                        ReleaseChannel.newBuilder()
                            .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED)
                    )
                    .build(),
            )
        }
    }
}
