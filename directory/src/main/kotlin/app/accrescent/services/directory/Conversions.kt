// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.v1.ReleaseChannel

/**
 * The permanent, canonical name of the stable release channel
 */
const val RELEASE_CHANNEL_NAME_STABLE = "well_known_stable"

/**
 * Maps a release channel into its canonical string form
 */
fun ReleaseChannel.canonicalForm(): String = this.wellKnown.canonicalForm()

/**
 * Maps a well-known release channel into its canonical string form
 */
fun ReleaseChannel.WellKnown.canonicalForm(): String = when (this) {
    ReleaseChannel.WellKnown.UNRECOGNIZED,
    ReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED,
    ReleaseChannel.WellKnown.WELL_KNOWN_STABLE -> RELEASE_CHANNEL_NAME_STABLE
}

/**
 * Maps a canonical release channel string into its corresponding release channel
 */
fun releaseChannelFromCanonicalForm(canonicalForm: String): ReleaseChannel {
    return ReleaseChannel.newBuilder()
        .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_STABLE)
        .build()
}
