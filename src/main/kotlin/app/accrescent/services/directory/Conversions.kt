// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.v1.ReleaseChannel

/**
 * Maps a release channel into its canonical string form
 */
fun ReleaseChannel.canonicalForm(): String = when (this.wellKnown) {
    ReleaseChannel.WellKnown.UNRECOGNIZED,
    ReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED,
    ReleaseChannel.WellKnown.WELL_KNOWN_STABLE -> {
        "well_known_stable"
    }
}

/**
 * Maps a canonical release channel string into its corresponding release channel
 */
fun releaseChannelFromCanonicalForm(canonicalForm: String): ReleaseChannel {
    return ReleaseChannel.newBuilder()
        .setWellKnown(ReleaseChannel.WellKnown.WELL_KNOWN_STABLE)
        .build()
}
