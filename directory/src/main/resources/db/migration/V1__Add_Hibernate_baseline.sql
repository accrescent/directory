CREATE SEQUENCE apks_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE images_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE apks (
    id bigint NOT NULL,
    apk_set_path text NOT NULL,
    object_id text NOT NULL UNIQUE,
    release_channel_id uuid NOT NULL,
    uncompressed_size integer NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (apk_set_path, release_channel_id)
);

CREATE TABLE apps (
    id text NOT NULL,
    default_listing_language text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE images (
    id bigint NOT NULL,
    object_id text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE listings (
    app_id text NOT NULL,
    language text NOT NULL,
    name text NOT NULL,
    short_description text NOT NULL,
    icon_image_id bigint NOT NULL,
    PRIMARY KEY (app_id, language)
);

CREATE TABLE release_channels (
    id uuid NOT NULL,
    app_id text NOT NULL,
    build_apks_result bytea NOT NULL,
    name text NOT NULL,
    version_code bigint NOT NULL,
    version_name text NOT NULL,
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
