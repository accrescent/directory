// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import java.time.LocalDate

/**
 * An event representing a client having checked an app for available updates.
 *
 * @property date the UTC date the check occurred
 * @property appId the unique app ID of the app checked for updates
 * @property releaseChannel the canonical name of the release channel checked for updates
 * @property deviceSdkVersion the Android SDK version of the requesting device
 * @property countryCode the ISO 3166-1 alpha-2 country code of the requesting client's geolocation
 */
data class AppUpdateAvailabilityChecked(
    val date: LocalDate,
    val appId: String,
    val releaseChannel: String,
    val deviceSdkVersion: UInt,
    val countryCode: String?,
)
