// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.v1beta1.DeviceAttributes
import com.android.bundle.Commands
import com.android.bundle.Devices
import com.android.tools.build.bundletool.device.ApkMatcher
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException
import java.util.Base64
import java.util.Optional

/**
 * Gets an app's matching APK object IDs for the given device
 *
 * @param appMetadata the `BuildApksResult` of the app with each APK path mapped to an object ID
 * @param deviceAttributes the device attributes of the device
 * @return a list of objects matching the given device, or an empty list if none match
 */
fun getMatchingApkObjectIds(
    appMetadata: Commands.BuildApksResult,
    deviceAttributes: DeviceAttributes,
): List<String> {
    val base64Encoder = Base64.getUrlEncoder()
    val base64Decoder = Base64.getUrlDecoder()

    // ApkMatcher validates the path format of each APK object ID we store in each ApkDescription's
    // path field. Thus, any valid object ID which is not also a valid path according to ApkMatcher
    // will be rejected. To get around this, we base64url encode each object ID before passing the
    // BuildApksResult to ApkMatcher, then decode the returned "paths" (which are actually base64url
    // encoded object IDs) back into the object IDs we need.
    val encodedBuildApksResult = appMetadata.toBuilder()
        .apply {
            variantBuilderList
                .flatMap { it.apkSetBuilderList }
                .flatMap { it.apkDescriptionBuilderList }
                .forEach { it.path = base64Encoder.encodeToString(it.path.toByteArray()) }
        }
        .build()

    val objectIds = try {
        ApkMatcher(
            Devices.DeviceSpec.parseFrom(deviceAttributes.spec.toByteArray()),
            Optional.empty(),
            true,
            false,
            true,
        ).getMatchingApks(encodedBuildApksResult)
    } catch (_: IncompatibleDeviceException) {
        emptyList()
    }.map { base64Decoder.decode(it.path.toString()).decodeToString() }

    return objectIds
}
