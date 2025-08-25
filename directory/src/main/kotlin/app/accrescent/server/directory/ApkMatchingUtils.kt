// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.bundletool.android.bundle.Commands
import app.accrescent.bundletool.android.bundle.Devices
import app.accrescent.directory.v1.DeviceAttributes
import com.android.tools.build.bundletool.device.ApkMatcher
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException
import java.util.Optional

/**
 * Gets an app's matching APK paths for the given device
 *
 * @param appMetadata the `BuildApksResult` of the app
 * @param deviceAttributes the device attributes of the device
 * @return a list of APK paths in the APK set associated with [appMetadata] and matching the given
 * device, or an empty list if none match
 */
fun getMatchingApkPaths(
    appMetadata: Commands.BuildApksResult,
    deviceAttributes: DeviceAttributes,
): List<String> {
    val paths = try {
        ApkMatcher(
            Devices.DeviceSpec.parseFrom(deviceAttributes.spec.toByteArray()),
            Optional.empty(),
            true,
            false,
            true,
        ).getMatchingApks(appMetadata)
    } catch (_: IncompatibleDeviceException) {
        emptyList()
    }.map { it.path.toString() }

    return paths
}
