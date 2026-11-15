-- IT Limerick Summit app: initial schema.
-- Every content table hangs off event_id so the app can be reused for future events.

CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT        NOT NULL,
    display_name    TEXT        NOT NULL,
    password_hash   TEXT,
    platform_admin  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

CREATE TABLE event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug           TEXT        NOT NULL UNIQUE,
    name           TEXT        NOT NULL,
    tagline        TEXT,
    description    TEXT,
    venue_name     TEXT,
    venue_address  TEXT,
    starts_at      TIMESTAMPTZ,
    ends_at        TIMESTAMPTZ,
    timezone       TEXT        NOT NULL DEFAULT 'Europe/Dublin',
    status         TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event_membership (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    event_id    UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL CHECK (role IN ('ATTENDEE', 'SPEAKER', 'SPONSOR', 'ORGANISER')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, event_id, role)
);

CREATE TABLE room (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID    NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    sort_order  INT     NOT NULL DEFAULT 0
);
CREATE INDEX ix_room_event ON room (event_id);

CREATE TABLE speaker (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    user_id       UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    name          TEXT        NOT NULL,
    headline      TEXT,
    company       TEXT,
    bio           TEXT,
    photo_url     TEXT,
    linkedin_url  TEXT,
    website_url   TEXT,
    published     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order    INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_speaker_event ON speaker (event_id);

CREATE TABLE agenda_session (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    room_id      UUID        REFERENCES room (id) ON DELETE SET NULL,
    title        TEXT        NOT NULL,
    description  TEXT,
    type         TEXT        NOT NULL DEFAULT 'TALK'
                 CHECK (type IN ('TALK', 'KEYNOTE', 'PANEL', 'WORKSHOP', 'BREAK', 'NETWORKING', 'OTHER')),
    starts_at    TIMESTAMPTZ NOT NULL,
    ends_at      TIMESTAMPTZ,
    status       TEXT        NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at IS NULL OR ends_at >= starts_at)
);
CREATE INDEX ix_session_event_start ON agenda_session (event_id, starts_at);

CREATE TABLE session_speaker (
    session_id  UUID NOT NULL REFERENCES agenda_session (id) ON DELETE CASCADE,
    speaker_id  UUID NOT NULL REFERENCES speaker (id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, speaker_id)
);

CREATE TABLE sponsor (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    name         TEXT        NOT NULL,
    tier         TEXT        NOT NULL DEFAULT 'Partner',
    tier_rank    INT         NOT NULL DEFAULT 100,
    description  TEXT,
    logo_url     TEXT,
    website_url  TEXT,
    sort_order   INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sponsor_event ON sponsor (event_id);

CREATE TABLE announcement (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    title         TEXT        NOT NULL,
    body          TEXT,
    priority      TEXT        NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('NORMAL', 'URGENT')),
    pinned        BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_announcement_event_published ON announcement (event_id, published_at DESC);

CREATE TABLE bookmark (
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    session_id  UUID        NOT NULL REFERENCES agenda_session (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, session_id)
);

-- Append-only record of what each user agreed to, and to which privacy notice version.
CREATE TABLE consent_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type            TEXT        NOT NULL CHECK (type IN ('PRIVACY_NOTICE', 'MARKETING')),
    notice_version  TEXT        NOT NULL,
    granted         BOOLEAN     NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_consent_user ON consent_record (user_id, type, recorded_at DESC);

-- Login sessions. Only a SHA-256 hash of the cookie value is stored.
CREATE TABLE auth_session (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash    TEXT        NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_auth_session_user ON auth_session (user_id);

CREATE TABLE password_reset_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
