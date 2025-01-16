// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.internal.v1.App
import app.accrescent.directory.internal.v1.App.PackageMetadataEntry
import app.accrescent.directory.internal.v1.AppListing
import app.accrescent.directory.internal.v1.CreateAppRequest
import app.accrescent.directory.internal.v1.CreateAppResponse
import app.accrescent.directory.internal.v1.DirectoryService
import app.accrescent.directory.internal.v1.Image
import app.accrescent.directory.internal.v1.PackageMetadata
import app.accrescent.directory.v1.ReleaseChannel
import com.android.bundle.Commands
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.quarkus.grpc.GrpcClient
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

private const val REQUEST_TIMEOUT_SECS: Long = 5

@QuarkusTest
class InternalDirectoryServiceImplTest {
    @GrpcClient
    lateinit var client: DirectoryService

    @ParameterizedTest
    @MethodSource("generateParamsForCreateAppValidatesRequest")
    fun createAppValidatesRequest(request: CreateAppRequest) {
        val response = CompletableFuture<Status.Code>()

        client.createApp(request).subscribe().with(
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

        client.createApp(validCreateAppRequest).subscribe().with(
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

        client.createApp(validCreateAppRequest)
            .onFailure()
            .invoke(Runnable { firstResponse.complete(null) })
            .chain { response ->
                firstResponse.complete(response)
                client.createApp(validCreateAppRequest)
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

    companion object {
        private val validCreateAppRequest: CreateAppRequest = CreateAppRequest.newBuilder()
            .setAppId("app.accrescent.client")
            .setApp(
                App.newBuilder()
                    .setDefaultListingLanguage("en")
                    .addListings(
                        AppListing.newBuilder()
                            .setLanguage("en")
                            .setName("Accrescent")
                            .setShortDescription("A private and secure Android app store")
                            .setIcon(
                                Image.newBuilder()
                                    .setObjectId("57297a7-6f2c-4a04-9656-497af21bf6b2")
                            )
                            .build()
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
                            )
                    )
                    .build()
            )
            .build()

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
                                AppListing
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
                                AppListing
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
                                AppListing
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
                                AppListing
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
                                AppListing
                                    .newBuilder()
                                    .mergeFrom(it)
                                    .apply {
                                        icon = Image
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
                    .build()
            )
        }
    }
}
