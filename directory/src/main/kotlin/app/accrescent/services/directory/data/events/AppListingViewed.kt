// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * An event representing a client having viewed an app listing.
 *
 * @property date the UTC date the listing view occurred
 * @property appId the unique app ID of the listing viewed
 * @property languageCode the BCP-47 language code of the listing viewed
 * @property deviceSdkVersion the Android SDK version of the requesting device
 * @property countryCode the ISO 3166-1 alpha-2 country code of the requesting client's geolocation
 */
data class AppListingViewed(
    val appId: String,
    val languageCode: String,
    val deviceSdkVersion: UInt,
    val countryCode: String?,
) {
    val date: LocalDate = LocalDate.now(ZoneOffset.UTC)
}
