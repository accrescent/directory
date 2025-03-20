// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import java.time.LocalDate

/**
 * An app download event
 *
 * @property date the date the download occurred
 * @property appId the app ID of the app downloaded
 * @property versionCode the version code of the app downloaded
 * @property deviceSdkVersion the Android SDK version of the requesting device
 * @property countryCode the ISO 3166-1 alpha-2 country code of the requesting client's geolocation
 */
data class Download(
    val date: LocalDate,
    val appId: String,
    val versionCode: UInt,
    val deviceSdkVersion: UInt,
    val countryCode: String?,
)
