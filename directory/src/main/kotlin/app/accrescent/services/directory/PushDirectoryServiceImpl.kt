// Copyright 2024-2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.com.android.bundle.Commands
import app.accrescent.directory.push.v1.App.PackageMetadataEntry
import app.accrescent.directory.push.v1.AppListing
import app.accrescent.directory.push.v1.CreateAppRequest
import app.accrescent.directory.push.v1.CreateAppResponse
import app.accrescent.directory.push.v1.ObjectMetadata
import app.accrescent.directory.push.v1.PackageMetadata
import app.accrescent.directory.push.v1.PushDirectoryService
import app.accrescent.services.directory.data.App
import app.accrescent.services.directory.data.AppRepository
import app.accrescent.services.directory.data.Image
import app.accrescent.services.directory.data.Listing
import app.accrescent.services.directory.data.ListingId
import app.accrescent.services.directory.data.ReleaseChannel
import app.accrescent.services.directory.data.StorageObject
import io.grpc.Status
import io.quarkus.grpc.GrpcService
import io.quarkus.hibernate.reactive.panache.common.WithTransaction
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import java.util.UUID
import app.accrescent.directory.push.v1.App as AppProto
import app.accrescent.directory.push.v1.Image as ImageProto
import app.accrescent.directory.v1beta1.ReleaseChannel as ReleaseChannelProto

/**
 * The server implementation of [PushDirectoryService]
 */
@GrpcService
class PushDirectoryServiceImpl @Inject constructor(
    private val appRepository: AppRepository,
) : PushDirectoryService {
    @WithTransaction
    override fun createApp(request: CreateAppRequest): Uni<CreateAppResponse> {
        val error = validateCreateAppRequest(request)
        if (error != null) {
            throw error.asRuntimeException()
        }

        val app = App(
            id = request.appId,
            defaultListingLanguage = request.app.defaultListingLanguage,
            listings = request.app.listingsList.mapTo(mutableSetOf<Listing>()) {
                Listing(
                    id = ListingId(
                        appId = request.appId,
                        language = it.language,
                    ),
                    name = it.name,
                    shortDescription = it.shortDescription,
                    icon = Image(
                        objectId = it.icon.objectId,
                    ),
                )
            },
            releaseChannels = request.app.packageMetadataList.mapTo(mutableSetOf<ReleaseChannel>()) {
                val releaseChannelId = UUID.randomUUID()

                ReleaseChannel(
                    id = releaseChannelId,
                    appId = request.appId,
                    name = it.releaseChannel.canonicalForm(),
                    versionCode = it.packageMetadata.versionCode.toUInt(),
                    versionName = it.packageMetadata.versionName,
                    buildApksResult = it.packageMetadata.buildApksResult.toByteArray(),
                    objects = it.packageMetadata.objectMetadataMap
                        .mapTo(mutableSetOf<StorageObject>()) {
                            StorageObject(
                                id = it.key,
                                releaseChannelId = releaseChannelId,
                                uncompressedSize = it.value.uncompressedSize.toUInt(),
                            )
                        },
                )
            }
        )

        val result = appRepository.deleteById(app.id)
            .chain { -> appRepository.persist(app) }
            .map { dbApp ->
                CreateAppResponse.newBuilder().setApp(
                    AppProto.newBuilder().apply {
                        defaultListingLanguage = dbApp.defaultListingLanguage
                        dbApp.listings.forEach { dbListing ->
                            addListings(
                                AppListing.newBuilder()
                                    .setLanguage(dbListing.id.language)
                                    .setName(dbListing.name)
                                    .setShortDescription(dbListing.shortDescription)
                                    .setIcon(
                                        ImageProto.newBuilder()
                                            .setObjectId(dbListing.icon.objectId)
                                    )
                            )
                        }
                        dbApp.releaseChannels.forEach { dbReleaseChannel ->
                            addPackageMetadata(
                                PackageMetadataEntry.newBuilder()
                                    .setReleaseChannel(
                                        releaseChannelFromCanonicalForm(dbReleaseChannel.name)
                                    )
                                    .setPackageMetadata(
                                        PackageMetadata.newBuilder()
                                            .setVersionCode(dbReleaseChannel.versionCode.toInt())
                                            .setVersionName(dbReleaseChannel.versionName)
                                            .setBuildApksResult(
                                                Commands.BuildApksResult
                                                    .parseFrom(dbReleaseChannel.buildApksResult)
                                            )
                                            .putAllObjectMetadata(dbReleaseChannel.objects.associate {
                                                it.id to ObjectMetadata
                                                    .newBuilder()
                                                    .setUncompressedSize(it.uncompressedSize.toInt())
                                                    .build()
                                            })
                                    )
                                    .build()
                            )
                        }
                    }.build()
                ).build()
            }

        return result
    }

    private companion object {
        private fun validateCreateAppRequest(request: CreateAppRequest): Status? = when {
            !request.hasAppId() -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("app ID is missing but required")

            !request.hasApp() -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("app metadata is missing but required")

            !request.app.hasDefaultListingLanguage() -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("default listing language is missing but required")

            request.app.listingsList.distinct().count() != request.app.listingsCount -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("listing languages must not be duplicated")

            request.app.listingsCount < 1 -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("no listings found but at least one required")

            !request.app.listingsList.any { it.language == request.app.defaultListingLanguage } ->
                Status.fromCode(Status.Code.INVALID_ARGUMENT)
                    .withDescription("no listing found for default listing language")

            !request.app.listingsList.all { it.hasLanguage() } -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all listings must specify a language")

            !request.app.listingsList.all { it.hasName() } -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all listings must specify a name")

            !request.app.listingsList.all { it.hasShortDescription() } -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all listings must specify a short description")

            !request.app.listingsList.all { it.hasIcon() } -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all listings must specify an icon")

            !request.app.listingsList.all { it.icon.hasObjectId() } -> Status
                .fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all listings icons must specify an object ID")

            !request.app.packageMetadataList.any {
                it.releaseChannel.hasWellKnown() &&
                        it.releaseChannel.wellKnown == ReleaseChannelProto.WellKnown.WELL_KNOWN_STABLE
            } -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("stable channel metadata must be provided")

            request.app.packageMetadataList.distinct().count() != request.app.packageMetadataCount
                -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("release channels must not be duplicated")

            !request.app.packageMetadataList.map { it.packageMetadata }
                .all { packageMetadata ->
                    packageMetadata.buildApksResult.variantList.flatMap { it.apkSetList }
                        .flatMap { it.apkDescriptionList }
                        .map { it.path }
                        .all { packageMetadata.objectMetadataMap.contains(it) }
                } -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all objects must have metadata specified")

            !request.app.packageMetadataList.map { it.packageMetadata }
                .all { packageMetadata ->
                    packageMetadata.buildApksResult.variantList.flatMap { it.apkSetList }
                        .flatMap { it.apkDescriptionList }
                        .map { it.path }
                        .all { packageMetadata.objectMetadataMap[it]?.hasUncompressedSize() == true }
                } -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("all objects must have an uncompressed size specified")

            !request.app.packageMetadataList.map { it.packageMetadata }
                .all { packageMetadata ->
                    val buildApksResultObjectIds = packageMetadata.buildApksResult.variantList
                        .flatMap { it.apkSetList }
                        .flatMap { it.apkDescriptionList }
                        .map { it.path }
                        .toSet()
                    packageMetadata.objectMetadataMap.keys.all { buildApksResultObjectIds.contains(it) }
                } -> Status.fromCode(Status.Code.INVALID_ARGUMENT)
                .withDescription("object metadata found for unspecified object")

            else -> null
        }
    }
}
