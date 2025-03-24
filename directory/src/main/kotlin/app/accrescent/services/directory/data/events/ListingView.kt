// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import java.time.LocalDate

/**
 * An app listing view event
 *
 * @property date the date the view occurred
 * @property appId the app ID of the listing viewed
 * @property languageCode the BCP-47 language code of the listing viewed
 * @property deviceSdkVersion the Android SDK version of the requesting device
 * @property countryCode the ISO 3166-1 alpha-2 country code of the requesting client's geolocation
 */
data class ListingView(
    val date: LocalDate,
    val appId: String,
    val languageCode: String,
    val deviceSdkVersion: UInt,
    val countryCode: String?,
)
