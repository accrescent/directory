// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import app.accrescent.appstore.v1.DeviceAttributes
import app.accrescent.bundletool.android.bundle.Commands
import app.accrescent.bundletool.android.bundle.Devices
import com.android.tools.build.bundletool.device.ApkMatcher
import com.android.tools.build.bundletool.model.AbiName
import com.android.tools.build.bundletool.model.exceptions.BundleToolException
import java.util.Optional

/**
 * Returns the first ABI in [deviceAttributes]'s device spec that bundletool does not recognize, or
 * `null` if every supported ABI is known.
 *
 * Lets callers reject unrecognized ABIs before matching, since bundletool otherwise surfaces them
 * inconsistently (an `IncompatibleDeviceException` for split APKs, an `InvalidCommandException` for
 * multi-ABI ones, or no error at all).
 */
fun firstUnrecognizedAbi(deviceAttributes: DeviceAttributes): String? =
    deviceAttributes.spec.supportedAbisList.firstOrNull { !AbiName.fromPlatformName(it).isPresent }

/**
 * Gets an app's matching APK paths for the given device
 *
 * @param appMetadata the `BuildApksResult` of the app
 * @param deviceAttributes the device attributes of the device
 * @return a list of APK paths in the APK set associated with [appMetadata] and matching the given
 * device, or an empty list if none match or bundletool rejects [deviceAttributes]
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
    } catch (_: BundleToolException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        // Some matchers (device_tier, country_set, reqGlEsVersion) reject the spec with a plain
        // IllegalArgumentException rather than a BundleToolException.
        emptyList()
    }.map { it.path.toString() }

    return paths
}
