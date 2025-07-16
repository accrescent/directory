// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import java.time.LocalDate

/**
 * An event representing a client having downloaded an app.
 *
 * @property date the UTC date the download occurred
 * @property appId the unique app ID of the app downloaded
 * @property versionCode the version code of the app downloaded
 * @property downloadType the type of download, such as "update"
 * @property deviceSdkVersion the Android SDK version of the requesting device
 * @property countryCode the ISO 3166-1 alpha-2 country code of the requesting client's geolocation
 */
data class AppDownloaded(
    val date: LocalDate,
    val appId: String,
    val versionCode: UInt,
    val downloadType: DownloadType,
    val deviceSdkVersion: UInt,
    val countryCode: String?,
)

/**
 * An app download type
 *
 * @property dbValue the database representation value of this enum
 */
enum class DownloadType(val dbValue: Short) {
    /**
     * An initial installation
     */
    INITIAL(1),

    /**
     * An update from an installed version
     */
    UPDATE(2),
}
