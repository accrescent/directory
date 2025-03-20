-- Copyright 2025 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

CREATE TABLE events.downloads (
    date Date,
    app_id String,
    version_code UInt32,
    device_sdk_version UInt16,
)
ENGINE = MergeTree
ORDER BY (app_id, date);
