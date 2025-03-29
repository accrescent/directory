// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import java.time.LocalDate

/**
 * An update check event
 *
 * @property date the date of the update check
 * @property appId the app ID being checked for updates
 * @property releaseChannel the canonical name of the release channel being checked for updates
 * @property deviceSdkVersion the Android SDK version of the requesting device
 * @property countryCode the ISO 3166-1 alpha-2 country code of the requesting client's geolocation
 */
data class UpdateCheck(
    val date: LocalDate,
    val appId: String,
    val releaseChannel: String,
    val deviceSdkVersion: UInt,
    val countryCode: String?,
)
