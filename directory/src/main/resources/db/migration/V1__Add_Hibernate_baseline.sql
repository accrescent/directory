CREATE SEQUENCE images_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE apks (
    id TEXT NOT NULL,
    release_channel_id uuid NOT NULL,
    uncompressed_size INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE apps (
    default_listing_language TEXT NOT NULL,
    id text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE images (
    id BIGINT NOT NULL,
    object_id TEXT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE listings (
    icon_image_id BIGINT NOT NULL,
    app_id TEXT NOT NULL,
    language TEXT NOT NULL,
    name TEXT NOT NULL,
    short_description TEXT NOT NULL,
    PRIMARY KEY (app_id, language)
);

CREATE TABLE release_channels (
    version_code INTEGER NOT NULL,
    id UUID NOT NULL,
    app_id TEXT NOT NULL,
    name TEXT NOT NULL,
    version_name TEXT NOT NULL,
    build_apks_result BYTEA NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_id, name)
);

ALTER TABLE IF EXISTS apks
    ADD CONSTRAINT FK9cd0kp980csf3we7o30i3g8k5
    FOREIGN KEY (release_channel_id)
    REFERENCES release_channels
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS listings
    ADD CONSTRAINT FKo8c72gcx6w3qc2q6w64rld402
    FOREIGN KEY (app_id)
    REFERENCES apps
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS listings
    ADD CONSTRAINT FKn540ygsm2x3dylvgggbrxyo6y
    FOREIGN KEY (icon_image_id)
    REFERENCES images
    ON DELETE CASCADE;

ALTER TABLE IF EXISTS release_channels
    ADD CONSTRAINT FKaykqr0sktefb3lrlqa6c5di2g
    FOREIGN KEY (app_id)
    REFERENCES apps
    ON DELETE CASCADE;
