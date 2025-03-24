-- Copyright 2025 Logan Magee
--
-- SPDX-License-Identifier: AGPL-3.0-only

CREATE TABLE downloads (
    date Date,
    app_id String,
    version_code UInt32,
    device_sdk_version UInt16,
    country_code LowCardinality(String),
)
ENGINE = MergeTree
ORDER BY (app_id, date, version_code, country_code);

CREATE TABLE listing_views (
    date Date,
    app_id String,
    language_code LowCardinality(String),
    device_sdk_version UInt16,
    country_code LowCardinality(String),
)
ENGINE = MergeTree
ORDER BY (app_id, date, language_code, country_code);
