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
 * @property deviceSdkVersion the Android SDK version of the requesting device
 */
data class Download(val date: LocalDate, val appId: String, val deviceSdkVersion: Int)
